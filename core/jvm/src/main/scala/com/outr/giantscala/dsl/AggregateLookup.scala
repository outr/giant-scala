package com.outr.giantscala.dsl

import com.outr.giantscala.{DBCollection, Field, ModelObject}
import io.circe.Json

case class AggregateLookup[Other <: ModelObject[Other], T](from: DBCollection[Other],
                                                           localField: Option[Field[T]],
                                                           foreignField: Option[Field[T]],
                                                           as: String,
                                                           let: List[ProjectField],
                                                           pipeline: List[AggregateInstruction]) extends AggregateInstruction {
  def pipeline(f: AggregateBuilder[Other, Other] => AggregateBuilder[Other, Other]): AggregateInstruction = {
    copy(pipeline = f(from.aggregate).pipeline)
  }

  override def json: Json = {
    val entries = List(
      Some("from" -> Json.fromString(from.collectionName)),
      localField.map(f => "localField" -> Json.fromString(f.fieldName)),
      foreignField.map(f => "foreignField" -> Json.fromString(f.fieldName)),
      if (let.nonEmpty) {
        Some("let" -> let.json)
      } else {
        None
      },
      if (pipeline.nonEmpty) {
        Some("pipeline" -> Json.arr(pipeline.map(_.json): _*))
      } else {
        None
      },
      Some("as" -> Json.fromString(as))
    ).flatten
    Json.obj("$lookup" -> Json.obj(entries: _*))
  }
}
