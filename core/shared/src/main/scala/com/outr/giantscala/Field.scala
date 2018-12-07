package com.outr.giantscala

class Field[T](val name: String)

object Field {
  def apply[T](name: String): Field[T] = new Field[T](name)
}