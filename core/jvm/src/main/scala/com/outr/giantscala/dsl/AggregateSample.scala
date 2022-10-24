package com.outr.giantscala.dsl

import fabric._

case class AggregateSample(size: Int) extends AggregateInstruction {
  override def json: Json = obj("$sample" -> obj("size" -> size))
}
