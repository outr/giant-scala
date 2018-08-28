package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import io.circe.Json

case class AggregateProject(fields: List[ProjectField]) extends AggregateInstruction {
  override def json: Json = {
    val projection = Json.obj(fields.map(f => f.key -> f.value): _*)
    Json.obj("$project" -> projection)
  }
}

sealed trait ProjectField {
  def key: String
  def value: Json
}

object ProjectField {
  case class Include[T](field: Field[T]) extends ProjectField {
    override def key: String = field.name
    override def value: Json = Json.fromInt(1)
  }
  case class Exclude[T](field: Field[T]) extends ProjectField {
    override def key: String = field.name
    override def value: Json = Json.fromInt(0)
  }
  case class Operator[T](field: Field[T], value: Json) extends ProjectField {
    override def key: String = field.name
  }
}