package com.outr.giantscala

import io.circe.Json

import scala.language.experimental.macros

class Field[T](val name: String) {
  def apply(value: T): Json = macro SharedMacros.fieldValue[T]
}

object Field {
  def apply[T](name: String): Field[T] = new Field[T](name)
}