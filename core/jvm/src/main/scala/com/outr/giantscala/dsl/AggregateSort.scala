package com.outr.giantscala.dsl

import fabric._

case class AggregateSort(sortFields: List[SortField]) extends AggregateInstruction {
  override def json: Json = {
    val sorting = sortFields.map { sf =>
      sf.fieldName -> num(sf.direction)
    }
    obj("$sort" -> obj(sorting: _*))
  }
}
