package com.outr.giantscala

import com.outr.giantscala.dsl._
import com.outr.giantscala.failure.{DBFailure, FailureType}
import com.outr.giantscala.oplog.CollectionMonitor
import io.circe.{Json, Printer}
import org.mongodb.scala.{BulkWriteResult, MongoCollection, MongoException, MongoNamespace}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal, in}
import org.mongodb.scala.model.RenameCollectionOptions
import org.mongodb.scala.result.DeleteResult

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

  private lazy val collection: MongoCollection[Document] = db.getCollection(collectionName)

  val converter: Converter[T]

  lazy val monitor: CollectionMonitor[T] = new CollectionMonitor[T](this, collection)

  def indexes: List[Index]

  def storedIndexes: Future[List[StoredIndex]] = collection.listIndexes().toFuture().map { documents =>
    documents.toList.map(StoredIndex.converter.fromDocument)
  }

  def create(): Future[Unit] = for {
    _ <- Future.sequence(indexes.map(_.create(collection)))
    stored <- storedIndexes
    delete = stored.collect {
      case storedIndex if !indexes.exists(_.fields.toSet == storedIndex.fields) && storedIndex.name != "_id_" => {
        scribe.warn(s"Deleting existing index: ${storedIndex.ns}.${storedIndex.name}")
        storedIndex
      }
    }
    _ <- Future.sequence(delete.map(i => collection.dropIndex(i.name).toFuture()))
  } yield {
    ()
  }

  lazy val batch: Batch[T] = Batch[T](this, collection)

  lazy val aggregate: AggregateBuilder[T, T] = AggregateBuilder(this, collection, converter)
  lazy val updateOne: UpdateBuilder[T] = UpdateBuilder[T](this, collection, many = false)
  lazy val updateMany: UpdateBuilder[T] = UpdateBuilder[T](this, collection, many = true)
  def replaceOne(replacement: T): ReplaceOneBuilder[T] = ReplaceOneBuilder[T](this, collection, replacement)

  def deleteOne(conditions: MatchCondition*): Future[Either[DBFailure, DeleteResult]] = {
    val json = conditions.foldLeft(Json.obj())((json, condition) => json.deepMerge(condition.json))
    collection.deleteOne(Document(json.pretty(Printer.spaces2))).toFuture().either
  }

  def deleteMany(conditions: MatchCondition*): Future[Either[DBFailure, DeleteResult]] = {
    val json = conditions.foldLeft(Json.obj())((json, condition) => json.deepMerge(condition.json))
    collection.deleteMany(Document(json.pretty(Printer.spaces2))).toFuture().either
  }

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
    replaceOne(value).`match`(Field[String]("_id") === value._id).upsert.toFuture.map(_ => value).either
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
    aggregate.sample(size).toFuture.either.flatMap {
      case Left(f) if f.`type` == FailureType.SampleNoNonDuplicate && retries > 0 => sample(size, retries - 1)
      case result => Future.successful(result)
    }
  }

  def largeSample(size: Int,
                  groupSize: Int,
                  retries: Int = 2,
                  samples: Set[T] = Set.empty): Future[Either[DBFailure, Set[T]]] = scribe.async {
    val querySize = math.min(size - samples.size, groupSize)
    if (querySize > 0) {
      sample(querySize, retries).flatMap {
        case Left(dbf) => Future.successful(Left(dbf))
        case Right(values) => {
          largeSample(size, groupSize, retries, samples ++ values)
        }
      }
    } else {
      Future.successful(Right(samples))
    }
  }

  def get(id: String): Future[Option[T]] = scribe.async {
    collection.find(Document("_id" -> id)).toFuture().map { documents =>
      documents.headOption.map(converter.fromDocument)
    }
  }

  def count(): Future[Long] = scribe.async(collection.estimatedDocumentCount().toFuture())

  def rename(newName: String, dropTarget: Boolean = false): Future[Either[DBFailure, Unit]] = {
    val options = new RenameCollectionOptions
    if (dropTarget) options.dropTarget(true)
    collection.renameCollection(MongoNamespace(newName), options).toFuture().map(_ => ()).either
  }

  def delete(id: String): Future[Either[DBFailure, Unit]] = scribe.async {
    collection.deleteOne(Document("_id" -> id)).toFuture().map(_ => ()).either
  }

  def delete(ids: Seq[String]): Future[Either[DBFailure, Int]] = scribe.async {
    collection.deleteMany(in("_id", ids: _*)).toFuture().map(_ => ids.length).either
  }

  def drop(): Future[Unit] = scribe.async(collection.drop().toFuture().map(_ => ()))
}