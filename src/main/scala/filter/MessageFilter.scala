package filter

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class Filter(fieldName: String, fieldValue: String,  op: String)
object Filter {
  implicit val encoder: JsonEncoder[Filter] = DeriveJsonEncoder.gen[Filter]
  implicit val decoder: JsonDecoder[Filter] = DeriveJsonDecoder.gen[Filter]
}

