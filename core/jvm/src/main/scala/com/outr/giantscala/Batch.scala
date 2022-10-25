package com.outr.giantscala

import cats.effect.IO
import org.mongodb.scala.{BulkWriteResult, MongoCollection}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model._

import org.mongodb.scala.model.Filters.equal

case class Batch[T <: ModelObject[T]](collection: DBCollection[T],
                                   mongoCollection: MongoCollection[Document],
                                   operations: List[WriteModel[Document]] = Nil,
                                   stopOnFailure: Boolean = false,
                                   bypassDocumentValidation: Boolean = false) extends StreamSupport {
  def withOps(ops: Seq[WriteModel[Document]]): Batch[T] = copy(operations = operations ::: ops.toList)
  def insert(values: T*): Batch[T] = withOps(values.map(v => InsertOneModel(collection.converter.toDocument(v))))
  def update(values: T*): Batch[T] = withOps(values.map(v => ReplaceOneModel(
    filter = equal("_id", v._id.value),
    replacement = collection.converter.toDocument(v)
  )))
  def upsert(values: T*): Batch[T] = withOps(values.map(v => ReplaceOneModel(
    filter = equal("_id", v._id.value),
    replacement = collection.converter.toDocument(v),
    replaceOptions = new ReplaceOptions().upsert(true)
  )))
  def delete(ids: Id[T]*): Batch[T] = {
    withOps(ids.map(id => DeleteOneModel(Document("_id" -> id.value))).asInstanceOf[Seq[WriteModel[Document]]])
  }
  def options(stopOnFailure: Boolean = this.stopOnFailure,
              bypassDocumentValidation: Boolean = this.bypassDocumentValidation): Batch[T] = {
    copy(stopOnFailure = stopOnFailure, bypassDocumentValidation = bypassDocumentValidation)
  }

  def execute(): IO[BulkWriteResult] = {
    val options = BulkWriteOptions().ordered(stopOnFailure).bypassDocumentValidation(bypassDocumentValidation)
    mongoCollection.bulkWrite(operations, options).one
  }
}