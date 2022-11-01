package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import fabric._
import fabric.rw.Convertible

case class PullModifier[F](field: Field[F], modifier: Json)

object PullModifier {
  def apply[F](field: Field[F], value: F): PullModifier[F] = PullModifier(field, value.json(field.rw))
  def in[F](field: Field[List[F]], values: List[F]): PullModifier[List[F]] = PullModifier(field, obj("$in" -> values.json(field.rw)))
}