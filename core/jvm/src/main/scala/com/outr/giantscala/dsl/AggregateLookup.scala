package com.outr.giantscala.dsl

import com.outr.giantscala.{DBCollection, Field, ModelObject}
import io.circe.Json

case class AggregateLookup[Other <: ModelObject, T](from: DBCollection[Other],
                                                    localField: Field[T],
                                                    foreignField: Field[T],
                                                    as: String) extends AggregateInstruction {
  override def json: Json = Json.obj(
    "$lookup" -> Json.obj(
      "from" -> Json.fromString(from.collectionName),
      "localField" -> Json.fromString(localField.name),
      "foreignField" -> Json.fromString(foreignField.name),
      "as" -> Json.fromString(as)
    )
  )
}
