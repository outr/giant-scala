package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import fabric._

case class AggregateProject(fields: List[ProjectField]) extends AggregateInstruction {
  override def json: Json = {
    val projection = fields.json
    obj("$project" -> projection)
  }
}

trait ProjectField extends AggregateInstruction

object ProjectField {
  case class Include[T](field: Field[T]) extends ProjectField {
    override def json: Json = obj(field.fieldName -> 1)
  }
  case class Exclude[T](field: Field[T]) extends ProjectField {
    override def json: Json = obj(field.fieldName -> 0)
  }
  case class Operator[T](field: Field[T], value: Json) extends ProjectField {
    override def json: Json = obj(field.fieldName -> value)
  }
}