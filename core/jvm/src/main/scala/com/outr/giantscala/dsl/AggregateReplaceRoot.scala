package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateReplaceRoot(newRoot: Json) extends AggregateInstruction {
  override def json: Json = Json.obj(
    "$replaceRoot" -> Json.obj(
      "newRoot" -> newRoot
    )
  )
}
