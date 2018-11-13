package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateLimit(limit: Int) extends AggregateInstruction {
  override def json: Json = Json.obj("$limit" -> Json.fromInt(limit))
}