package com.outr.giantscala

import com.mongodb.client.model.UpdateOptions
import com.outr.giantscala.failure.DBFailure
import com.outr.giantscala.oplog.CollectionMonitor
import org.mongodb.scala.{MongoCollection, MongoException}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal, in}

import scala.language.experimental.macros
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions
import scala.util.Success

abstract class DBCollection[T <: ModelObject](val name: String, val db: MongoDatabase) {
  db.addCollection(this)

  implicit class EnhancedFuture[Result](future: Future[Result]) {
    def either: Future[Either[DBFailure, Result]] = {
      val promise = Promise[Either[DBFailure, Result]]
      future.onComplete {
        case scala.util.Failure(t) => t match {
          case exc: MongoException => promise.success(Left(DBFailure(exc)))
          case _ => promise.failure(t)
        }
        case Success(result) => promise.success(Right(result))
      }
      promise.future
    }
  }

  lazy val collection: MongoCollection[Document] = db.getCollection(name)

  val converter: Converter[T]

  lazy val monitor: CollectionMonitor[T] = new CollectionMonitor[T](this)

  def indexes: List[Index]

  def create(): Future[Unit] = Future.sequence(indexes.map(_.create(collection))).map(_ => ())    // Create indexes

  def insert(values: Seq[T]): Future[Seq[T]] = if (values.nonEmpty) {
    val docs = values.map(converter.toDocument)
    collection.insertMany(docs).toFuture().map(_ => values)
  } else {
    Future.successful(Nil)
  }

  def insert(value: T): Future[Either[DBFailure, T]] = {
    val document = converter.toDocument(value)
    collection.insertOne(document).toFuture().map(_ => value).either
  }

  def update(value: T): Future[Either[DBFailure, T]] = {
    val doc = converter.toDocument(value)
    collection.replaceOne(equal("_id", value._id), doc).toFuture().map(_ => value).either
  }

  def upsert(value: T): Future[Either[DBFailure, T]] = {
    val doc = converter.toDocument(value)
    collection.replaceOne(equal("_id", value._id), doc, new UpdateOptions().upsert(true)).toFuture().map(_ => value).either
  }

  def byIds(ids: Seq[String]): Future[List[T]] = {
    collection.find(in("_id", ids: _*)).toFuture().map { documents =>
      documents.map(converter.fromDocument).toList
    }
  }

  def all(limit: Int = 1000): Future[List[T]] = {
    collection.find().limit(limit).toFuture().map { documents =>
      documents.map(converter.fromDocument).toList
    }
  }

  def get(id: String): Future[Option[T]] = collection.find(Document("_id" -> id)).toFuture().map { documents =>
    documents.headOption.map(converter.fromDocument)
  }

  def count(): Future[Long] = collection.count().toFuture()

  def delete(id: String): Future[Either[DBFailure, Unit]] = collection.deleteOne(Document("_id" -> id)).toFuture().map(_ => ()).either

  def delete(ids: Seq[String]): Future[Either[DBFailure, Int]] = {
    collection.deleteMany(in("_id", ids: _*)).toFuture().map(_ => ids.length).either
  }

  def drop(): Future[Unit] = collection.drop().toFuture().map(_ => ())
}