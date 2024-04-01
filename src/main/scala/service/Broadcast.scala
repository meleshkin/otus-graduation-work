package service

import filter.Filter
import message.Message
import zio.stm._
import zio.stream.{ZSink, ZStream}
import zio.{Task, URLayer, ZLayer}

trait Broadcast {
  def subscribe(filter: Filter): Task[TDequeue[Message]]
}

object MessageBroadcast {
  val live: URLayer[MessageBuffer, Broadcast] =
    ZLayer {
      for {
        hubs <- TMap.empty[Filter, THub[Message]].commit
        _ <-
          ZStream
            .repeatZIO(MessageBuffer.get())
            .mapZIO { message =>
              val publish = for {
                maybeHub <- filter(message, hubs)
                _ <- maybeHub match {
                  case Some (hub) =>
                    hub.publish(message).unit
                  case _ =>
                    STM.unit
                }
              } yield ()
              publish.commit
            }
            .run(ZSink.drain)
            .forkDaemon
      } yield MessageBroadcast(hubs)
    }

  def filter(message: Message, hubs: TMap[Filter, THub[Message]]) = {
    val maybeHub: ZSTM[Any, Nothing, Option[Filter]] = for {
      filters <- hubs.keys
      result = filters.find(f => f.fieldValue.equals(message.classType))
    } yield result

    maybeHub.flatMap({
      case Some(value) => hubs.get(value)
      case None => ZSTM.none
    })
  }
}

case class MessageBroadcast(hubs: TMap[Filter, THub[Message]]) extends Broadcast {

  override def subscribe(filter: Filter): Task[TDequeue[Message]] = {
    val dequeue: USTM[TDequeue[Message]] = for {
      hub <- newHub(filter)
      res <- hub.subscribe
    } yield res
    dequeue.commit
  }

  def newHub(filter: Filter): USTM[THub[Message]] = {
    val hh = hubs.get(filter)
    hh.flatMap {
      case Some(hub) => {
        STM.succeed(hub)
      }
      case _ => {
        THub.unbounded[Message].tap(hubs.put(filter, _))
      }
    }
  }
}

