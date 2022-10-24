package com.outr.giantscala.dsl

import fabric._

case class AggregateOut(collectionName: String) extends AggregateInstruction {
  override def json: Json = obj("$out" -> str(collectionName))
}
