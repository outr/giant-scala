package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateAddFields(fields: List[ProjectField]) extends AggregateInstruction {
  override def json: Json = {
    val projection = fields.json
    Json.obj("$addFields" -> projection)
  }
}