package com.outr.giantscala

import fabric._

case class FieldAndValue[T](field: Field[T], value: T) {
  lazy val json: Json = obj(field.fieldName -> field.rw.read(value))
}