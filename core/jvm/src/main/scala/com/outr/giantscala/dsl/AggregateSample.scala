package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateSample(size: Int) extends AggregateInstruction {
  override def json: Json = Json.obj("$sample" -> Json.obj("size" -> Json.fromInt(size)))
}
