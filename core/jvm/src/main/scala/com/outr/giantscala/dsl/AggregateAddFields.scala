package com.outr.giantscala.dsl

import fabric._

case class AggregateAddFields(fields: List[ProjectField]) extends AggregateInstruction {
  override def json: Json = {
    val projection = fields.json
    obj("$addFields" -> projection)
  }
}