package com.outr.giantscala.dsl

import cats.effect.IO
import com.mongodb.client.model.UpdateOptions
import com.outr.giantscala.{DBCollection, Field, ModelObject, StreamSupport}
import fabric._
import fabric.io.JsonFormatter
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.UpdateResult

case class UpdateBuilder[Type <: ModelObject[Type]](collection: DBCollection[Type],
                                              mongoCollection: MongoCollection[Document],
                                              many: Boolean,
                                              conditions: List[MatchCondition] = Nil,
                                              modifications: Map[String, Json] = Map.empty,
                                              upsert: Boolean = false) extends StreamSupport {
  def `match`(conditions: MatchCondition*): UpdateBuilder[Type] = {
    copy(conditions = this.conditions ::: conditions.toList)
  }
  def filter(conditions: MatchCondition*): UpdateBuilder[Type] = `match`(conditions: _*)
  def set(values: Json*): UpdateBuilder[Type] = withModifications("$set", values: _*)
  def setOnInsert(values: Json*): UpdateBuilder[Type] = withModifications("$setOnInsert", values: _*)
  def unset(fields: Field[_]*): UpdateBuilder[Type] = {
    withModifications("$unset", fields.map(f => obj(f.fieldName -> str(""))): _*)
  }
  def rename(tuples: (String, String)*): UpdateBuilder[Type] = {
    withModifications("$rename", tuples.map {
      case (previous, updated) => obj(previous -> str(updated))
    }: _*)
  }
  def inc(values: Json*): UpdateBuilder[Type] = withModifications("$inc", values: _*)
  def min(values: Json*): UpdateBuilder[Type] = withModifications("$min", values: _*)
  def max(values: Json*): UpdateBuilder[Type] = withModifications("$max", values: _*)
  def mult(values: Json*): UpdateBuilder[Type] = withModifications("$mult", values: _*)
  def withModifications(key: String, values: Json*): UpdateBuilder[Type] = {
    val json = values.foldLeft[Json](obj())((j1, j2) => j1.merge(j2))
    val merged = modifications.getOrElse(key, obj()).merge(json)
    copy(modifications = modifications + (key -> merged))
  }
  def withUpdate: UpdateBuilder[Type] = copy(upsert = false)
  def withUpsert: UpdateBuilder[Type] = copy(upsert = true)

  def toIO: IO[UpdateResult] = {
    val filter = Document(JsonFormatter.Default(conditions.map(_.json).foldLeft[Json](obj())((j1, j2) => j1.merge(j2))))
    val update = Document(JsonFormatter.Default(modifications.map {
      case (key, json) => obj(key -> json)
    }.foldLeft[Json](obj())((j1, j2) => j1.merge(j2))))
    val options = new UpdateOptions
    options.upsert(upsert)
    if (many) {
      mongoCollection.updateMany(filter, update, options).one
    } else {
      mongoCollection.updateOne(filter, update, options).one
    }
  }
}