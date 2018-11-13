package com.outr.giantscala.dsl

import io.circe.Json

case class AggregateSort(sortFields: List[SortField]) extends AggregateInstruction {
  override def json: Json = {
    val sorting = sortFields.map { sf =>
      sf.fieldName -> Json.fromInt(sf.direction)
    }
    Json.obj("$sort" -> Json.obj(sorting: _*))
  }
}
