package com.outr.giantscala.dsl

import fabric._

case class AggregateSkip(skip: Int) extends AggregateInstruction {
  override def json: Json = obj("$skip" -> skip)
}
