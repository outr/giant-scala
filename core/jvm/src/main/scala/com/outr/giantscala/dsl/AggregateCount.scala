package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateCount(fieldName: String) extends AggregateInstruction {
  override def json: Json = Json.obj("$count" -> Json.fromString(fieldName))
}
