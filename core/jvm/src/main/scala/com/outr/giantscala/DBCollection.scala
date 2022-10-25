package com.outr.giantscala

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.outr.giantscala.dsl._
import com.outr.giantscala.failure.{DBFailure, FailureType}
import fabric._
import fabric.io.JsonFormatter
import org.mongodb.scala.{BulkWriteResult, MongoCollection, MongoException, MongoNamespace}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal, in}
import org.mongodb.scala.model.RenameCollectionOptions
import org.mongodb.scala.result.DeleteResult

import scala.language.experimental.macros
import scala.language.implicitConversions

abstract class DBCollection[T <: ModelObject[T]](val collectionName: String, val db: MongoDatabase) extends Implicits with StreamSupport {
  db.addCollection(this)

  implicit class EnhancedIO[Result](io: IO[Result]) {
    def either: IO[Either[DBFailure, Result]] = io.map[Either[DBFailure, Result]](Right.apply).redeem(
      recover = (t: Throwable) => t match {
        case exc: MongoException => Left(DBFailure(exc))
        case _ => throw t
      },
      map = identity
    )
  }

  private lazy val collection: MongoCollection[Document] = db.getCollection(collectionName)

  val converter: Converter[T]

//  lazy val monitor: CollectionMonitor[T] = new CollectionMonitor[T](this, collection)

  def id(id: String): Id[T] = Id[T](id)

  def indexes: List[Index]

  def storedIndexes: IO[List[StoredIndex]] = collection.listIndexes().toList.map { documents =>
    documents.map(StoredIndex.converter.fromDocument)
  }

  def create(): IO[Unit] = for {
    _ <- indexes.map(_.create(collection)).sequence
    stored <- storedIndexes
    delete = stored.collect {
      case storedIndex if !indexes.exists(_.fields.map(_.fieldName).toSet == storedIndex.fields) && storedIndex.name != "_id_" => {
        scribe.warn(s"Deleting existing index: ${storedIndex.ns}.${storedIndex.name}")
        storedIndex
      }
    }
    _ <- delete.map(i => collection.dropIndex(i.name).toList).sequence
  } yield {
    ()
  }

  lazy val batch: Batch[T] = Batch[T](this, collection)

  lazy val aggregate: AggregateBuilder[T, T] = AggregateBuilder(this, collection, converter)
  lazy val updateOne: UpdateBuilder[T] = UpdateBuilder[T](this, collection, many = false)
  lazy val updateMany: UpdateBuilder[T] = UpdateBuilder[T](this, collection, many = true)
  def replaceOne(replacement: T): ReplaceOneBuilder[T] = ReplaceOneBuilder[T](this, collection, replacement)

  def deleteOne(conditions: MatchCondition*): IO[Either[DBFailure, DeleteResult]] = {
    val json = conditions.foldLeft[Json](obj())((json, condition) => json.merge(condition.json))
    collection.deleteOne(Document(JsonFormatter.Default(json))).one.either
  }

  def deleteMany(conditions: MatchCondition*): IO[Either[DBFailure, DeleteResult]] = {
    val json = conditions.foldLeft[Json](obj())((json, condition) => json.merge(condition.json))
    collection.deleteMany(Document(JsonFormatter.Default(json))).one.either
  }

  def insert(values: Seq[T]): IO[Either[DBFailure, Seq[T]]] = {
    if (values.nonEmpty) {
      val docs = values.map(converter.toDocument)
      collection.insertMany(docs).first.map(_ => values).either
    } else {
      IO.pure(Right(Nil))
    }
  }

  def insert(value: T): IO[Either[DBFailure, T]] = {
    val document = converter.toDocument(value)
    collection.insertOne(document).first.map(_ => value).either
  }

  def update(value: T): IO[Either[DBFailure, T]] = {
    val doc = converter.toDocument(value)
    collection.replaceOne(equal("_id", value._id.value), doc).first.map(_ => value).either
  }

  def update(values: Seq[T]): IO[BulkWriteResult] = {
    var b = batch
    values.foreach { v =>
      b = b.update(v)
    }
    b.execute()
  }

  def upsert(value: T): IO[Either[DBFailure, T]] = {
    replaceOne(value).`match`(Field[Id[T]]("_id") === value._id).upsert.toIO.map(_ => value).either
  }

  def upsert(values: Seq[T]): IO[BulkWriteResult] = {
    var b = batch
    values.foreach { v =>
      b = b.upsert(v)
    }
    b.execute()
  }

  def byIds(ids: Seq[Id[T]]): IO[List[T]] = {
    collection.find(in("_id", ids.map(_.value): _*)).toList.map { documents =>
      documents.map(converter.fromDocument)
    }
  }

  def all(limit: Int = 1000): IO[List[T]] = {
    collection.find().limit(limit).toList.map { documents =>
      documents.map(converter.fromDocument)
    }
  }

  def sample(size: Int, retries: Int = 2): IO[Either[DBFailure, List[T]]] = {
    aggregate.sample(size).toList.either.flatMap {
      case Left(f) if f.`type` == FailureType.SampleNoNonDuplicate && retries > 0 => sample(size, retries - 1)
      case result => IO.pure(result)
    }
  }

  def largeSample(size: Int,
                  groupSize: Int,
                  retries: Int = 2,
                  samples: Set[T] = Set.empty): IO[Either[DBFailure, Set[T]]] = {
    val querySize = math.min(size - samples.size, groupSize)
    if (querySize > 0) {
      sample(querySize, retries).flatMap {
        case Left(dbf) => IO.pure(Left(dbf))
        case Right(values) => {
          val merged = samples ++ values
          if (merged == samples) {
            scribe.warn(s"Reached maximum samples: ${merged.size}, wanted $querySize more but could not find more samples")
            IO.pure(Right(merged))
          } else {
            largeSample(size, groupSize, retries, merged)
          }
        }
      }
    } else {
      IO.pure(Right(samples))
    }
  }

  def get(id: Id[T]): IO[Option[T]] = {
    collection.find(Document("_id" -> id.value)).toList.map { documents =>
      documents.headOption.map(converter.fromDocument)
    }
  }

  def count(): IO[Long] = collection.estimatedDocumentCount().one

  def rename(newName: String, dropTarget: Boolean = false): IO[Either[DBFailure, Unit]] = {
    val options = new RenameCollectionOptions
    if (dropTarget) options.dropTarget(true)
    collection.renameCollection(MongoNamespace(newName), options).first.map(_ => ()).either
  }

  def delete(id: Id[T]): IO[Either[DBFailure, Unit]] = {
    collection.deleteOne(Document("_id" -> id.value)).first.map(_ => ()).either
  }

  def delete(ids: Seq[Id[T]]): IO[Either[DBFailure, Int]] = {
    collection.deleteMany(in("_id", ids.map(_.value): _*)).first.map(_ => ids.length).either
  }

  def drop(): IO[Unit] = collection.drop().first.map(_ => ())
}