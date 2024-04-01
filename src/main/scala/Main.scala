import io.getquill.cassandrazio.Quill
import io.getquill._
import service.{BrokerConsumer, Cassandra, Config, KafkaBrokerConsumer, KafkaMessageBuffer, MessageBroadcast, MessageBuffer, WebServer, ServerImpl}
import zio.config.typesafe.FromConfigSourceTypesafe
import zio.stream.ZSink
import zio.{ConfigProvider, ExitCode, Runtime, Scope, Task, TaskLayer, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}


object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(ConfigProvider.fromResourcePath())


  def program(config: TaskLayer[Config]): Task[ExitCode] = {
    for {
      consume <- BrokerConsumer.consume
      _ <- consume.mapZIO(MessageBuffer.put).run(ZSink.drain).forkDaemon
      serve <- WebServer.serve
    } yield serve
  }.provide(config, KafkaBrokerConsumer.live,
    KafkaMessageBuffer.live,
    MessageBroadcast.live,
    ServerImpl.live,
    Cassandra.live,
    Quill.CassandraZioSession.fromPrefix("cassandra"),
    Quill.Cassandra.fromNamingStrategy(Literal))

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = program(Config.live)
}
