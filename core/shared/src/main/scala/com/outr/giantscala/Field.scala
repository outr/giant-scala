package com.outr.giantscala

import io.circe.Json

import scala.language.experimental.macros

class Field[T](val fieldName: String) {
  def apply(value: T): Json = macro SharedMacros.fieldValue[T]

  def opt: Field[Option[T]] = Field[Option[T]](fieldName)
}

object Field {
  lazy val Root: Field[Unit] = new Field[Unit]("$ROOT")

  def apply[T](name: String): Field[T] = new Field[T](name)
}