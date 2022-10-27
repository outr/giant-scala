package com.outr.giantscala.dsl

import cats.effect.IO
import com.outr.giantscala.{DBCollection, Field, FieldAndValue, ModelObject, StreamSupport}
import fabric._
import fabric.io.JsonFormatter
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument}

case class FindAndUpdateBuilder[Type <: ModelObject[Type]](collection: DBCollection[Type],
                                                           mongoCollection: MongoCollection[Document],
                                                           conditions: List[MatchCondition] = Nil,
                                                           modifications: Map[String, Json] = Map.empty) extends StreamSupport {
  def `match`(conditions: MatchCondition*): FindAndUpdateBuilder[Type] = {
    copy(conditions = this.conditions ::: conditions.toList)
  }

  def filter(conditions: MatchCondition*): FindAndUpdateBuilder[Type] = `match`(conditions: _*)

  def currentDate(field: Field[Long]): FindAndUpdateBuilder[Type] =
    withModifications("$currentDate", obj(field.fieldName -> true))

  def inc[T <: Numeric[T]](field: Field[T], increment: T): FindAndUpdateBuilder[Type] =
    withModifications("$inc", obj(
      field.fieldName -> field.rw.read(increment)
    ))

  def min[T <: Numeric[T]](field: Field[T], value: T): FindAndUpdateBuilder[Type] =
    withModifications("$min", obj(
      field.fieldName -> field.rw.read(value)
    ))

  def max[T <: Numeric[T]](field: Field[T], value: T): FindAndUpdateBuilder[Type] =
    withModifications("$max", obj(
      field.fieldName -> field.rw.read(value)
    ))

  def mul[T <: Numeric[T]](field: Field[T], value: T): FindAndUpdateBuilder[Type] =
    withModifications("$mul", obj(
      field.fieldName -> field.rw.read(value)
    ))

  def rename[T](existingField: Field[T], newField: Field[T]): FindAndUpdateBuilder[Type] =
    withModifications("$rename", obj(
      existingField.fieldName -> newField.fieldName
    ))

  def set(fieldAndValues: FieldAndValue[_]*): FindAndUpdateBuilder[Type] =
    withModifications("$set", fieldAndValues.map(_.json): _*)

  def unset(field: Field[_]): FindAndUpdateBuilder[Type] =
    withModifications("$unset", obj(
      field.fieldName -> ""
    ))

  def withModifications(key: String, values: Json*): FindAndUpdateBuilder[Type] = {
    val json = values.foldLeft[Json](obj())((j1, j2) => j1.merge(j2))
    val merged = modifications.getOrElse(key, obj()).merge(json)
    copy(modifications = modifications + (key -> merged))
  }

  private lazy val filterDocument = Document(JsonFormatter.Default(conditions.map(_.json).foldLeft[Json](obj())((j1, j2) => j1.merge(j2))))
  private lazy val updateDocument = Document(JsonFormatter.Default(modifications.map {
    case (key, json) => obj(key -> json)
  }.foldLeft[Json](obj())((j1, j2) => j1.merge(j2))))

  def toIO: IO[Option[Type]] = {
    val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
    mongoCollection.findOneAndUpdate(
      filter = filterDocument,
      update = updateDocument,
      options = options
    ).first.map(_.map(collection.converter.fromDocument))
  }
}