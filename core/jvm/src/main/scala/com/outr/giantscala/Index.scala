package com.outr.giantscala

import com.mongodb.client.model.{CollationStrength, IndexOptions}
import com.outr.giantscala.Index._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions
import org.mongodb.scala.model.Collation

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.TimeUnit

case class Index private(`type`: IndexType, fields: List[String], properties: IndexProperties = IndexProperties()) {
  private[giantscala] def create(collection: MongoCollection[Document]): Future[Unit] = {
    assert(fields.nonEmpty, "An index must contain at least one field!")

    val ndx = toBSON(this)
    val options = new IndexOptions
    properties.name.foreach(options.name)
    properties.unique.foreach(options.unique)
    properties.expireAfter.foreach(ea => options.expireAfter(ea.value, ea.unit))
    properties.collation.foreach(options.collation)
    properties.sparse.foreach(options.sparse)

    collection.createIndex(ndx, options).toFuture().map(_ => ())
  }

  def property(name: Option[String] = properties.name,
               unique: Option[Boolean] = properties.unique,
               expireAfter: Option[ExpireAfter] = properties.expireAfter,
               collation: Option[Collation] = properties.collation,
               sparse: Option[Boolean] = properties.sparse): Index = {
    copy(properties = IndexProperties(name, unique, expireAfter, collation, sparse))
  }

  def name(name: String): Index = property(name = Some(name))
  def unique: Index = property(unique = Some(true))
  def expireAfter(value: Long, unit: TimeUnit): Index = property(expireAfter = Some(ExpireAfter(value, unit)))
  def caseInsensitive(locale: String = "en"): Index = {
    val c = Collation.builder().locale(locale).collationStrength(CollationStrength.SECONDARY).build()
    property(collation = Some(c))
  }
}

case class IndexProperties(name: Option[String] = None,
                           unique: Option[Boolean] = None,
                           expireAfter: Option[ExpireAfter] = None,
                           collation: Option[Collation] = None,
                           sparse: Option[Boolean] = None)

case class ExpireAfter(value: Long, unit: TimeUnit)

object Index {
  sealed trait IndexType {
    def apply(fields: String*): Index = Index(this, fields.toList)
    def multiple(fields: String*): List[Index] = fields.toList.map(apply(_))
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