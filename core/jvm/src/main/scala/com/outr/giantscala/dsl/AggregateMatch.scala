package com.outr.giantscala.dsl

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.`match`
import org.mongodb.scala.model.Filters.and

case class AggregateMatch(conditions: List[MatchCondition]) extends AggregateInstruction {
  override lazy val bson: Bson = `match`(and(conditions.map(_.bson): _*))
}
