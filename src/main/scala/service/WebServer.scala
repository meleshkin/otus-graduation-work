package service

import cats.syntax.all._
import filter.Filter
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import sttp.ws.WebSocketFrame
import zio._
import zio.interop.catz._
import zio.json._
import zio.stream.{Stream, ZStream}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

trait WebServer {
  val serve: Task[ExitCode]
}

object WebServer {
  val serve: ZIO[WebServer, Throwable, ExitCode] = ZIO.serviceWithZIO[WebServer](_.serve)
}

final case class ServerImpl(port: Int, broadcast: Broadcast, cassandra: Cassandra, ec: ExecutionContext) extends WebServer {
  var filterId = 0;
  val filters = collection.mutable.Map[String,  Filter]()
  val default = Filter("classType", "*", "eq");
  filters.addOne(nextFilterId, default)
  filters.addOne(nextFilterId, Filter("classType", "AlertCreated", "eq"))
  filters.addOne(nextFilterId, Filter("classType", "AlertAcknowledged", "eq"))

  private val createFilter: ZServerEndpoint[Any, Any] = {
    endpoint.post
      .in("filter")
      .in(jsonBody[Filter])
      .out(stringBody)
      .zServerLogic(filter => {
        maybeCreateFilter(filter)
      })
  }

  private val getFilter: ZServerEndpoint[Any, Any] = {
    endpoint.get
      .in("filter" /path[String]("filterId"))
      .out(jsonBody[Filter])
      .zServerLogic(filterId => {
        ZIO.succeed {
          filters(filterId)
        }
      })
  }


  private val listFilters: ZServerEndpoint[Any, Any] = {
    endpoint.get
      .errorOut(plainBody[String])
      .in("filter")
      .out(jsonBody[List[(String, Filter)]])
      .zServerLogic(_ => {
        ZIO.succeed {
          filters.toList
        }
      })
  }

  private val enableFilterToDb: ZServerEndpoint[Any, Any] = {
    endpoint.get
      .in("filter" /path[String] ("filterId") / "act")
      .zServerLogic(filterId => {
        val filter = filters.getOrElse(filterId, default)
        cassandra.enableFilter(filter).forkDaemon zipRight ZIO.unit
      })
  }

  private val enableFilterToWS: ZServerEndpoint[Any, ZioStreams & WebSockets] =
    endpoint.get
      .in("ws" / "filter" / path[String]("filterId"))
      .out(webSocketBodyRaw(ZioStreams))
      .zServerLogic(filterId => enableFilterToWSLLogic(filters.getOrElse(filterId, default)))

  private def enableFilterToWSLLogic(filter: Filter): UIO[Stream[Throwable, WebSocketFrame] => Stream[Throwable, WebSocketFrame]] = {
      val res = ZIO.succeed { (_: Stream[Throwable, WebSocketFrame]) =>
        val out = for {
          dequeue <- broadcast.subscribe(filter)
          messages= ZStream
            .fromTQueue(dequeue)
            .map(msg => {
              val s = WebSocketFrame.text(msg.toJson)
              s
            })
        } yield messages

        ZStream.unwrap(out)
      }
     res
  }

  private val enableFilterToWsRoutes: WebSocketBuilder2[Task] => HttpRoutes[Task] = ZHttp4sServerInterpreter().fromWebSocket(enableFilterToWS).toRoutes
  private val enableFilterToDbRoutes = ZHttp4sServerInterpreter().from(enableFilterToDb).toRoutes
  private val createFilterRoutes = ZHttp4sServerInterpreter().from(createFilter).toRoutes
  private val listFiltersRoutes = ZHttp4sServerInterpreter().from(listFilters).toRoutes
  private val getFilterRoutes = ZHttp4sServerInterpreter().from(getFilter).toRoutes

  private def nextFilterId: String = {
    filterId = filterId + 1
    String.valueOf(filterId)
  }


  private def maybeCreateFilter(filter: Filter): UIO[String] = {
    ZIO.succeed {
      filters.find(f => f._2 == filter) match {
        case Some(value) => value._1
        case None => val id = nextFilterId
          filters.put(id, filter)
          id
      }
    }
  }

  override val serve: Task[ExitCode] =
    BlazeServerBuilder[Task]
      .withExecutionContext(ec)
      .bindHttp(port, "localhost")
      .withHttpWebSocketApp(wsb => Router("/" -> enableFilterToDbRoutes
        .combineK(enableFilterToWsRoutes(wsb))
        .combineK(createFilterRoutes)
        .combineK(listFiltersRoutes)
        .combineK(getFilterRoutes)
       ).orNotFound)
      .serve
      .compile
      .drain
      .exitCode
}

object ServerImpl {
  val live: URLayer[service.Config & Broadcast & Cassandra, WebServer] =
    ZLayer {
      for {
        port      <- ZIO.serviceWith[service.Config](_.port)
        broadcast <- ZIO.service[Broadcast]
        cassandra <- ZIO.service[Cassandra]
        executor  <- ZIO.executor
      } yield ServerImpl(port, broadcast, cassandra, executor.asExecutionContext)
    }
}