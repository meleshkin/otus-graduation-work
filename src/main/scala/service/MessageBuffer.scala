package service

import message.Message
import zio.stm.TQueue
import zio.{UIO, URIO, ZIO, ZLayer}

trait MessageBuffer {
  def put(message: Message): UIO[Unit]

  def get(): UIO[Message]
}

object MessageBuffer {
  def put(message: Message): URIO[MessageBuffer, Unit] = {
    ZIO.serviceWithZIO[MessageBuffer](buf => buf.put(message))
  }

  def get(): URIO[MessageBuffer, Message] = {
    ZIO.serviceWithZIO[MessageBuffer](buf => buf.get())
  }
}

final case class KafkaMessageBuffer(queue: TQueue[Message]) extends MessageBuffer {
  override def put(message: Message): UIO[Unit] = {
    (
      for {
        _ <- queue.offer(message).unit
      } yield ()
    ).commit
  }

  override def get(): UIO[Message] = {
    (
      for {
        res <- queue.take
      } yield res
    ).commit
  }
}

object KafkaMessageBuffer {
  val live: ZLayer[Any, Nothing, KafkaMessageBuffer] = ZLayer.fromZIO {
    val queue = TQueue.unbounded[Message].commit
    for {
      q <- queue
      kmb = KafkaMessageBuffer(q)
    } yield kmb
  }
}
