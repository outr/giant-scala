package com.outr.giantscala.dsl

import fabric._

case class AggregateMatch(conditions: List[MatchCondition]) extends AggregateInstruction {
  override def json: Json = {
    val value = conditions match {
      case condition :: Nil => condition.json
      case _ => obj("$and" -> arr(conditions.map(_.json): _*))
    }
    obj("$match" -> value)
  }
}