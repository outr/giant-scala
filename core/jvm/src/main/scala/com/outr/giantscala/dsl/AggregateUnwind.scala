package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateUnwind(path: String) extends AggregateInstruction {
  override def json: Json = Json.obj("$unwind" -> Json.fromString(path))
}
