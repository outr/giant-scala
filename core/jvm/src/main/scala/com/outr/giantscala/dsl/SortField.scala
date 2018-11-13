package com.outr.giantscala.dsl

import com.outr.giantscala.Field

sealed trait SortField {
  def fieldName: String
  def direction: Int
}

object SortField {
  case class Ascending[T](field: Field[T]) extends SortField {
    override def fieldName: String = field.name
    override def direction: Int = 1
  }
  case class Descending[T](field: Field[T]) extends SortField {
    override def fieldName: String = field.name
    override def direction: Int = -1
  }
}