package com.outr.giantscala

import com.outr.giantscala.Index._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Index private(`type`: IndexType, fields: String*) {
  // collection.createIndex(ascending("emails")).toFuture()
  private[giantscala] def create(collection: MongoCollection[Document]): Future[Unit] = {
    assert(fields.nonEmpty, "An index must contain at least one field!")

    val ndx = toBSON(this)
    collection.createIndex(ndx).toFuture().map(_ => ())
  }
}

object Index {
  sealed trait IndexType {
    def apply(fields: String*): Index = Index(this, fields: _*)
  }

  case object Ascending extends IndexType
  case object Descending extends IndexType
  case object Text extends IndexType
  case object Hashed extends IndexType
  case class Compound(indexes: Index*) extends IndexType

  private def toBSON(index: Index): conversions.Bson = {
    import org.mongodb.scala.model.Indexes._

    index.`type` match {
      case Ascending => ascending(index.fields: _*)
      case Descending => descending(index.fields: _*)
      case Text => {
        assert(index.fields.lengthCompare(1) == 0, "A Text index must include exactly one index!")
        text(index.fields.head)
      }
      case Hashed => {
        assert(index.fields.lengthCompare(1) == 0, "A Hashed index must include exactly one index!")
        hashed(index.fields.head)
      }
      case c: Compound => {
        assert(c.indexes.nonEmpty, "A Compount index must have at least one child index!")
        compoundIndex(c.indexes.map(Index.toBSON): _*)
      }
    }
  }
}