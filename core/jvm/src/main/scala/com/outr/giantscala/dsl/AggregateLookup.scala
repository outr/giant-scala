package com.outr.giantscala.dsl

import com.outr.giantscala.{DBCollection, Field, ModelObject}
import io.circe.Json

case class AggregateLookup[Other <: ModelObject[Other], T](from: DBCollection[Other],
                                                           localField: Field[T],
                                                           foreignField: Field[T],
                                                           as: String) extends AggregateInstruction {
  override def json: Json = Json.obj(
    "$lookup" -> Json.obj(
      "from" -> Json.fromString(from.collectionName),
      "localField" -> Json.fromString(localField.fieldName),
      "foreignField" -> Json.fromString(foreignField.fieldName),
      "as" -> Json.fromString(as)
    )
  )
}
