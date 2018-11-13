package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateSkip(skip: Int) extends AggregateInstruction {
  override def json: Json = Json.obj("$skip" -> Json.fromInt(skip))
}
