package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import io.circe.Json

case class AggregateProject(fields: List[ProjectField]) extends AggregateInstruction {
  override def json: Json = {
    val projection = fields.json
    Json.obj("$project" -> projection)
  }
}

trait ProjectField extends AggregateInstruction

object ProjectField {
  case class Include[T](field: Field[T]) extends ProjectField {
    override def json: Json = Json.obj(field.name -> Json.fromInt(1))
  }
  case class Exclude[T](field: Field[T]) extends ProjectField {
    override def json: Json = Json.obj(field.name -> Json.fromInt(0))
  }
  case class Operator[T](field: Field[T], value: Json) extends ProjectField {
    override def json: Json = Json.obj(field.name -> value)
  }
}