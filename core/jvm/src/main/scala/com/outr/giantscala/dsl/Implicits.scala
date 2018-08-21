package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import org.mongodb.scala.model.{Filters => f}

trait Implicits {
  implicit class FieldExtras[T](field: Field[T]) {
    def ===(value: T): MatchCondition = MatchCondition(f.equal(field.name, value))
    def in(values: T*): MatchCondition = MatchCondition(f.in(field.name, values: _*))
  }
}