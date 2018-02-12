package com.matthicks.giantscala

import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal, in}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class DBCollection[T <: ModelObject](name: String, db: MongoDatabase) {
  db.addCollection(this)

  lazy val collection: MongoCollection[Document] = db.getCollection(name)

  protected def document2T(document: Document): T
  protected def t2Document(t: T): Document

  def create(): Future[Unit] = Future.successful(())

  def insert(values: Seq[T]): Future[Seq[T]] = if (values.nonEmpty) {
    val docs = values.map(t2Document)
    collection.insertMany(docs).toFuture().map(_ => values)
  } else {
    Future.successful(Nil)
  }

  def insert(value: T): Future[T] = {
    val document = t2Document(value)
    collection.insertOne(document).toFuture().map(_ => value)
  }

  def update(value: T): Future[T] = {
    val doc = t2Document(value)
    collection.replaceOne(equal("_id", value._id), doc).toFuture().map(_ => value)
  }

  def byIds(ids: Seq[String]): Future[List[T]] = {
    collection.find(in("_id", ids: _*)).toFuture().map { documents =>
      documents.map(document2T).toList
    }
  }

  def all(limit: Int = 1000): Future[List[T]] = {
    collection.find().limit(limit).toFuture().map { documents =>
      documents.map(document2T).toList
    }
  }

  def get(id: String): Future[Option[T]] = collection.find(Document("_id" -> id)).toFuture().map { documents =>
    documents.headOption.map(document2T)
  }

  def delete(id: String): Future[Unit] = collection.deleteOne(Document("_id" -> id)).toFuture().map(_ => ())

  def delete(ids: Seq[String]): Future[Int] = {
    collection.deleteMany(in("_id", ids: _*)).toFuture().map(_ => ids.length)
  }

  def drop(): Future[Unit] = collection.drop().toFuture().map(_ => ())
}