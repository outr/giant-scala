package com.outr.giantscala

import fabric.rw.RW

import scala.language.experimental.macros

case class Field[T](fieldName: String)(implicit val rw: RW[T]) {
  def opt: Field[Option[T]] = Field[Option[T]](fieldName)
}

object Field {
  lazy val Root: Field[Unit] = new Field[Unit]("$ROOT")
}