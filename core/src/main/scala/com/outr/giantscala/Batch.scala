package com.outr.giantscala

import org.mongodb.scala.BulkWriteResult
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model._

import scala.concurrent.Future
import org.mongodb.scala.model.Filters.{equal, in}

case class Batch[T <: ModelObject](collection: DBCollection[T],
                                   operations: List[WriteModel[Document]] = Nil,
                                   stopOnFailure: Boolean = false,
                                   bypassDocumentValidation: Boolean = false) {
  def withOps(ops: Seq[WriteModel[Document]]): Batch[T] = copy(operations = operations ::: ops.toList)
  def insert(values: T*): Batch[T] = withOps(values.map(v => InsertOneModel(collection.converter.toDocument(v))))
  def update(values: T*): Batch[T] = withOps(values.map(v => ReplaceOneModel(
    filter = equal("_id", v._id),
    replacement = collection.converter.toDocument(v)
  )))
  def upsert(values: T*): Batch[T] = withOps(values.map(v => ReplaceOneModel(
    filter = equal("_id", v._id),
    replacement = collection.converter.toDocument(v),
    replaceOptions = new ReplaceOptions().upsert(true)
  )))
  def delete(ids: String*): Batch[T] = {
    withOps(ids.map(id => DeleteOneModel(Document("_id" -> id))).asInstanceOf[Seq[WriteModel[Document]]])
  }
  def options(stopOnFailure: Boolean = this.stopOnFailure,
              bypassDocumentValidation: Boolean = this.bypassDocumentValidation): Batch[T] = {
    copy(stopOnFailure = stopOnFailure, bypassDocumentValidation = bypassDocumentValidation)
  }

  def execute(): Future[BulkWriteResult] = {
    val options = BulkWriteOptions().ordered(stopOnFailure).bypassDocumentValidation(bypassDocumentValidation)
    collection.collection.bulkWrite(operations, options).toFuture()
  }
}