package com.outr.giantscala

import fabric.rw.RW

case class Id[T](value: String = Unique()) extends AnyVal {
  override def toString: String = s"Id($value)"
}

object Id {
  implicit def rw[T]: RW[Id[T]] = RW.string[Id[T]](
    asString = _.value,
    fromString = (value: String) => new Id[T](value)
  )
}