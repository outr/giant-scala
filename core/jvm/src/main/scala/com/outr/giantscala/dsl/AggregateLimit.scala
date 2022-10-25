package com.outr.giantscala.dsl

import fabric._

case class AggregateLimit(limit: Int) extends AggregateInstruction {
  override def json: Json = obj("$limit" -> limit)
}