package com.outr.giantscala.dsl

import fabric._

case class AggregateReplaceRoot(newRoot: Json) extends AggregateInstruction {
  override def json: Json = obj(
    "$replaceRoot" -> obj(
      "newRoot" -> newRoot
    )
  )
}
