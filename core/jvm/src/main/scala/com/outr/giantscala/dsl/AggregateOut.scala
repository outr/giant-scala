package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateOut(collectionName: String) extends AggregateInstruction {
  override def json: Json = Json.obj("$out" -> Json.fromString(collectionName))
}
