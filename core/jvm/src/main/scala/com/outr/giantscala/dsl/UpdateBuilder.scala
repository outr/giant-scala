package com.outr.giantscala.dsl

import com.mongodb.client.model.UpdateOptions
import com.outr.giantscala.{DBCollection, Field, ModelObject}
import io.circe.{Json, Printer}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContext, Future}

case class UpdateBuilder[Type <: ModelObject](collection: DBCollection[Type],
                                              many: Boolean,
                                              conditions: List[MatchCondition] = Nil,
                                              modifications: Map[String, Json] = Map.empty,
                                              upsert: Boolean = false) {
  def `match`(conditions: MatchCondition*): UpdateBuilder[Type] = {
    copy(conditions = this.conditions ::: conditions.toList)
  }
  def filter(conditions: MatchCondition*): UpdateBuilder[Type] = `match`(conditions: _*)
  def set(values: Json*): UpdateBuilder[Type] = withModifications("$set", values: _*)
  def setOnInsert(values: Json*): UpdateBuilder[Type] = withModifications("$setOnInsert", values: _*)
  def unset(fields: Field[_]*): UpdateBuilder[Type] = {
    withModifications("$unset", fields.map(f => Json.obj(f.name -> Json.fromString(""))): _*)
  }
  def rename(tuples: (String, String)*): UpdateBuilder[Type] = {
    withModifications("$rename", tuples.map {
      case (previous, updated) => Json.obj(previous -> Json.fromString(updated))
    }: _*)
  }
  def inc(values: Json*): UpdateBuilder[Type] = withModifications("$inc", values: _*)
  def min(values: Json*): UpdateBuilder[Type] = withModifications("$min", values: _*)
  def max(values: Json*): UpdateBuilder[Type] = withModifications("$max", values: _*)
  def mult(values: Json*): UpdateBuilder[Type] = withModifications("$mult", values: _*)
  def withModifications(key: String, values: Json*): UpdateBuilder[Type] = {
    val json = values.foldLeft(Json.obj())((j1, j2) => j1.deepMerge(j2))
    val merged = modifications.getOrElse(key, Json.obj()).deepMerge(json)
    copy(modifications = modifications + (key -> merged))
  }
  def withUpdate: UpdateBuilder[Type] = copy(upsert = false)
  def withUpsert: UpdateBuilder[Type] = copy(upsert = true)

  def toFuture(implicit executionContext: ExecutionContext): Future[UpdateResult] = {
    val filter = Document(conditions.map(_.json).foldLeft(Json.obj())((j1, j2) => j1.deepMerge(j2)).pretty(Printer.spaces2))
    val update = Document(modifications.map {
      case (key, json) => Json.obj(key -> json)
    }.foldLeft(Json.obj())((j1, j2) => j1.deepMerge(j2)).pretty(Printer.spaces2))
    val options = new UpdateOptions
    options.upsert(upsert)
    if (many) {
      collection.collection.updateMany(filter, update, options).toFuture()
    } else {
      collection.collection.updateOne(filter, update, options).toFuture()
    }
  }
}