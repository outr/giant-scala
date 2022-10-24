package com.outr.giantscala

import com.outr.giantscala.dsl._
import com.outr.giantscala.failure.{DBFailure, FailureType}
import com.outr.giantscala.oplog.CollectionMonitor
import fabric._
import fabric.io.JsonFormatter
import org.mongodb.scala.{BulkWriteResult, MongoCollection, MongoException, MongoNamespace}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal, in}
import org.mongodb.scala.model.RenameCollectionOptions
import org.mongodb.scala.result.DeleteResult

import scala.language.experimental.macros
import scala.concurrent.Future
import scribe.Execution.global

import scala.language.implicitConversions

abstract class DBCollection[T <: ModelObject[T]](val collectionName: String, val db: MongoDatabase) extends Implicits {
  db.addCollection(this)

  implicit class EnhancedFuture[Result](future: Future[Result]) {
    def either: Future[Either[DBFailure, Result]] = future.map[Either[DBFailure, Result]](Right.apply).recover {
      case exc: MongoException => Left(DBFailure(exc))
    }
  }

  private lazy val collection: MongoCollection[Document] = db.getCollection(collectionName)

  val converter: Converter[T]

  lazy val monitor: CollectionMonitor[T] = new CollectionMonitor[T](this, collection)

  def id(id: String): Id[T] = Id[T](id)

  def indexes: List[Index]

  def storedIndexes: Future[List[StoredIndex]] = collection.listIndexes().toFuture().map { documents =>
    documents.toList.map(StoredIndex.converter.fromDocument)
  }

  def create(): Future[Unit] = for {
    _ <- Future.sequence(indexes.map(_.create(collection)))
    stored <- storedIndexes
    delete = stored.collect {
      case storedIndex if !indexes.exists(_.fields.map(_.fieldName).toSet == storedIndex.fields) && storedIndex.name != "_id_" => {
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
    val json = conditions.foldLeft[Json](obj())((json, condition) => json.merge(condition.json))
    collection.deleteOne(Document(JsonFormatter.Default(json))).toFuture().either
  }

  def deleteMany(conditions: MatchCondition*): Future[Either[DBFailure, DeleteResult]] = {
    val json = conditions.foldLeft[Json](obj())((json, condition) => json.merge(condition.json))
    collection.deleteMany(Document(JsonFormatter.Default(json))).toFuture().either
  }

  def insert(values: Seq[T]): Future[Either[DBFailure, Seq[T]]] = {
    if (values.nonEmpty) {
      val docs = values.map(converter.toDocument)
      collection.insertMany(docs).toFuture().map(_ => values).either
    } else {
      Future.successful(Right(Nil))
    }
  }

  def insert(value: T): Future[Either[DBFailure, T]] = {
    val document = converter.toDocument(value)
    collection.insertOne(document).toFuture().map(_ => value).either
  }

  def update(value: T): Future[Either[DBFailure, T]] = {
    val doc = converter.toDocument(value)
    collection.replaceOne(equal("_id", value._id.value), doc).toFuture().map(_ => value).either
  }

  def update(values: Seq[T]): Future[BulkWriteResult] = {
    var b = batch
    values.foreach { v =>
      b = b.update(v)
    }
    b.execute()
  }

  def upsert(value: T): Future[Either[DBFailure, T]] = {
    replaceOne(value).`match`(Field[Id[T]]("_id") === value._id).upsert.toFuture.map(_ => value).either
  }

  def upsert(values: Seq[T]): Future[BulkWriteResult] = {
    var b = batch
    values.foreach { v =>
      b = b.upsert(v)
    }
    b.execute()
  }

  def byIds(ids: Seq[Id[T]]): Future[List[T]] = {
    collection.find(in("_id", ids.map(_.value): _*)).toFuture().map { documents =>
      documents.map(converter.fromDocument).toList
    }
  }

  def all(limit: Int = 1000): Future[List[T]] = {
    collection.find().limit(limit).toFuture().map { documents =>
      documents.map(converter.fromDocument).toList
    }
  }

  def sample(size: Int, retries: Int = 2): Future[Either[DBFailure, List[T]]] = {
    aggregate.sample(size).toFuture.either.flatMap {
      case Left(f) if f.`type` == FailureType.SampleNoNonDuplicate && retries > 0 => sample(size, retries - 1)
      case result => Future.successful(result)
    }
  }

  def largeSample(size: Int,
                  groupSize: Int,
                  retries: Int = 2,
                  samples: Set[T] = Set.empty): Future[Either[DBFailure, Set[T]]] = {
    val querySize = math.min(size - samples.size, groupSize)
    if (querySize > 0) {
      sample(querySize, retries).flatMap {
        case Left(dbf) => Future.successful(Left(dbf))
        case Right(values) => {
          val merged = samples ++ values
          if (merged == samples) {
            scribe.warn(s"Reached maximum samples: ${merged.size}, wanted $querySize more but could not find more samples")
            Future.successful(Right(merged))
          } else {
            largeSample(size, groupSize, retries, merged)
          }
        }
      }
    } else {
      Future.successful(Right(samples))
    }
  }

  def get(id: Id[T]): Future[Option[T]] = {
    collection.find(Document("_id" -> id.value)).toFuture().map { documents =>
      documents.headOption.map(converter.fromDocument)
    }
  }

  def count(): Future[Long] = collection.estimatedDocumentCount().toFuture()

  def rename(newName: String, dropTarget: Boolean = false): Future[Either[DBFailure, Unit]] = {
    val options = new RenameCollectionOptions
    if (dropTarget) options.dropTarget(true)
    collection.renameCollection(MongoNamespace(newName), options).toFuture().map(_ => ()).either
  }

  def delete(id: Id[T]): Future[Either[DBFailure, Unit]] = {
    collection.deleteOne(Document("_id" -> id.value)).toFuture().map(_ => ()).either
  }

  def delete(ids: Seq[Id[T]]): Future[Either[DBFailure, Int]] = {
    collection.deleteMany(in("_id", ids.map(_.value): _*)).toFuture().map(_ => ids.length).either
  }

  def drop(): Future[Unit] = collection.drop().toFuture().map(_ => ())
}