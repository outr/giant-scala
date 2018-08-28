package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateMatch(conditions: List[MatchCondition]) extends AggregateInstruction {
  override def json: Json = Json.obj("$match" -> Json.obj("$and" -> Json.arr(conditions.map(_.json): _*)))
}
