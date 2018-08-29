package com.outr.giantscala.dsl
import io.circe.Json

case class AggregateGroup(fields: List[ProjectField]) extends AggregateInstruction {
  override def json: Json = {
    val projection = fields.json
    Json.obj("$group" -> projection)
  }
}
