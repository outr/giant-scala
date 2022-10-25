package com.outr.giantscala.dsl

import fabric._

case class AggregateCount(fieldName: String) extends AggregateInstruction {
  override def json: Json = obj("$count" -> str(fieldName))
}
