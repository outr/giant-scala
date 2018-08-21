package com.outr.giantscala

import com.outr.giantscala.dsl.{AggregateBuilder, Implicits}
import com.outr.giantscala.failure.{DBFailure, FailureType}
import com.outr.giantscala.oplog.CollectionMonitor
import org.mongodb.scala.{BulkWriteResult, MongoCollection, MongoException}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal, in}
import org.mongodb.scala.model.{Aggregates, ReplaceOptions}

import scala.language.experimental.macros
import scala.concurrent.Future
import scribe.Execution.global

import scala.language.implicitConversions

abstract class DBCollection[T <: ModelObject](val collectionName: String, val db: MongoDatabase) extends Implicits {
  db.addCollection(this)

  implicit class EnhancedFuture[Result](future: Future[Result]) {
    def either: Future[Either[DBFailure, Result]] = future.map[Either[DBFailure, Result]](Right.apply).recover {
      case exc: MongoException => Left(DBFailure(scribe.Position.fix(exc)))
    }
  }

  lazy val collection: MongoCollection[Document] = db.getCollection(collectionName)

  val converter: Converter[T]

  lazy val monitor: CollectionMonitor[T] = new CollectionMonitor[T](this)

  def indexes: List[Index]

  def create(): Future[Unit] = Future.sequence(indexes.map(_.create(collection))).map(_ => ())    // Create indexes

  lazy val batch: Batch[T] = Batch[T](this)

  lazy val aggregate: AggregateBuilder[T, T] = AggregateBuilder(this, converter)

  def insert(values: Seq[T]): Future[Either[DBFailure, Seq[T]]] = scribe.async {
    if (values.nonEmpty) {
      val docs = values.map(converter.toDocument)
      collection.insertMany(docs).toFuture().map(_ => values).either
    } else {
      Future.successful(Right(Nil))
    }
  }

  def insert(value: T): Future[Either[DBFailure, T]] = scribe.async {
    val document = converter.toDocument(value)
    collection.insertOne(document).toFuture().map(_ => value).either
  }

  def update(value: T): Future[Either[DBFailure, T]] = scribe.async {
    val doc = converter.toDocument(value)
    collection.replaceOne(equal("_id", value._id), doc).toFuture().map(_ => value).either
  }

  def update(values: Seq[T]): Future[BulkWriteResult] = scribe.async {
    var b = batch
    values.foreach { v =>
      b = b.update(v)
    }
    b.execute()
  }

  def upsert(value: T): Future[Either[DBFailure, T]] = scribe.async {
    val doc = converter.toDocument(value)
    collection.replaceOne(equal("_id", value._id), doc, new ReplaceOptions().upsert(true)).toFuture().map(_ => value).either
  }

  def upsert(values: Seq[T]): Future[BulkWriteResult] = scribe.async {
    var b = batch
    values.foreach { v =>
      b = b.upsert(v)
    }
    b.execute()
  }

  def byIds(ids: Seq[String]): Future[List[T]] = scribe.async {
    collection.find(in("_id", ids: _*)).toFuture().map { documents =>
      documents.map(converter.fromDocument).toList
    }
  }

  def all(limit: Int = 1000): Future[List[T]] = scribe.async {
    collection.find().limit(limit).toFuture().map { documents =>
      documents.map(converter.fromDocument).toList
    }
  }

  def sample(size: Int, retries: Int = 2): Future[Either[DBFailure, List[T]]] = scribe.async {
    collection.aggregate(List(
      Aggregates.sample(size)
    )).toFuture()
      .map(_.map(converter.fromDocument).toList).either.flatMap {
        case Left(f) if f.`type` == FailureType.SampleNoNonDuplicate && retries > 0 => sample(size, retries - 1)
        case result => Future.successful(result)
    }
  }

  def get(id: String): Future[Option[T]] = scribe.async {
    collection.find(Document("_id" -> id)).toFuture().map { documents =>
      documents.headOption.map(converter.fromDocument)
    }
  }

  def count(): Future[Long] = scribe.async(collection.estimatedDocumentCount().toFuture())

  def delete(id: String): Future[Either[DBFailure, Unit]] = scribe.async {
    collection.deleteOne(Document("_id" -> id)).toFuture().map(_ => ()).either
  }

  def delete(ids: Seq[String]): Future[Either[DBFailure, Int]] = scribe.async {
    collection.deleteMany(in("_id", ids: _*)).toFuture().map(_ => ids.length).either
  }

  def drop(): Future[Unit] = scribe.async(collection.drop().toFuture().map(_ => ()))
}