package com.outr.giantscala.dsl

import fabric._

case class AggregateUnwind(path: String) extends AggregateInstruction {
  override def json: Json = obj("$unwind" -> str(path))
}
