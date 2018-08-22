package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.collection.mutable.{Document => MDocument}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.project

case class AggregateProject(fields: List[ProjectField]) extends AggregateInstruction {
  override lazy val bson: Bson = {
    val document = MDocument()
    fields.foreach(_.build(document))
    project(document)
  }
}

sealed trait ProjectField {
  def build(document: MDocument): Unit
}

object ProjectField {
  case class Include[T](field: Field[T]) extends ProjectField {
    override def build(document: MDocument): Unit = document += field.name -> 1
  }
  case class Exclude[T](field: Field[T]) extends ProjectField {
    override def build(document: MDocument): Unit = document += field.name -> 0
  }
  case class Operator[T](field: Field[T], value: Document) extends ProjectField {
    override def build(document: MDocument): Unit = document += field.name -> value
  }
}