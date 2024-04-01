package message

import zio.json._

case class Message(classType: String, probableCause: String, specificCause: String, severity: String, nodeId: Int, dateTimeModified: Long)
object Message {
  implicit val encoder: JsonEncoder[Message] = DeriveJsonEncoder.gen[Message]
  implicit val decoder: JsonDecoder[Message] = DeriveJsonDecoder.gen[Message]
}


case class Msg(uid: String, filter_hash: String, message: String)