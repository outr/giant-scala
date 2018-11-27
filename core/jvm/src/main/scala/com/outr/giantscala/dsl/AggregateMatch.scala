package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateMatch(conditions: List[MatchCondition]) extends AggregateInstruction {
  override def json: Json = {
    val value = conditions match {
      case condition :: Nil => condition.json
      case _ => Json.obj("$and" -> Json.arr(conditions.map(_.json): _*))
    }
    Json.obj("$match" -> value)
  }
}
