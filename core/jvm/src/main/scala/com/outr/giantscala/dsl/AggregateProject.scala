package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{addFields, project}

case class AggregateProject(included: List[Field[_]]) extends AggregateInstruction {
  override lazy val bson: Bson = project(
    addFields(included.map(f => new com.mongodb.client.model.Field(f.name, null)): _*)
  )
}
