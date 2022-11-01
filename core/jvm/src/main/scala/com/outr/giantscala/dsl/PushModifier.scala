package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import fabric._
import fabric.rw.Convertible

case class PushModifier[F](field: Field[F], modifier: Json)

object PushModifier {
  def apply[F](field: Field[F], value: F): PushModifier[F] = PushModifier(field, value.json(field.rw))
  def each[F](field: Field[List[F]], values: List[F]): PushModifier[List[F]] = PushModifier(field, obj("$each" -> values.json(field.rw)))
}