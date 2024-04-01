package service

import filter.Filter
import io.getquill.cassandrazio.Quill
import io.getquill.{EntityQuery, Literal, Quoted}
import message.{Message, Msg}
import zio.json.EncoderOps
import zio.stream.ZStream
import zio.{&, Task, URLayer, ZIO, ZLayer}

trait Cassandra {
  def enableFilter(filter: Filter): ZIO[Any, Throwable, Unit]
}

object Cassandra {
  val live: URLayer[Broadcast & Quill.Cassandra[Literal], Cassandra] = {
    val res = for {
      broadcast <- ZIO.service[Broadcast]
      quill <- ZIO.service[Quill.Cassandra[Literal]]
      cassandra = CassandreService(broadcast, quill)
    } yield cassandra

    ZLayer.fromZIO(res)
  }
}

case class CassandreService(broadcast: Broadcast, quill: Quill.Cassandra[Literal]) extends Cassandra {

  import quill._

  val messageSchema: Quoted[EntityQuery[Msg]] = quote {
    querySchema[Msg]("""message""")
  }
  def save(message: Msg): Task[Unit] = {
    quill.run(messageSchema.insertValue(lift(message)))
  }

  override def enableFilter(filter: Filter): ZIO[Any, Throwable, Unit] = {
    val task = broadcast.subscribe(filter)
    val stream: ZIO[Any, Throwable, ZStream[Any, Nothing, Message]] = task.map(queue => {
      ZStream.fromTQueue(queue)
    })
    val unwrapStream: ZStream[Any, Throwable, Message] = ZStream.unwrap(stream)
    unwrapStream.foreach(msg => {
      save(Msg(java.util.UUID.randomUUID.toString, filter.toString, msg.toJson))
    })
  }
}
