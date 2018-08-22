package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import org.mongodb.scala.bson.{BsonInt32, BsonString}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.collection.mutable.{Document => MDocument}
import org.mongodb.scala.model.{Filters => f}

trait Implicits {
  implicit class FieldExtras[T](field: Field[T]) {
    def ===(value: T): MatchCondition = MatchCondition(f.equal(field.name, value))
    def in(values: T*): MatchCondition = MatchCondition(f.in(field.name, values: _*))
    def include: ProjectField = ProjectField.Include(field)
    def exclude: ProjectField = ProjectField.Exclude(field)
    def objectToArray(arrayName: String): ProjectField = {
      val name = opKey(arrayName)
      ProjectField.Operator(field, Document("$objectToArray" -> name))
    }
    def addToSet(key: String): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(field, Document("$addToSet" -> name))
    }
    def arrayElemAt(key: String, index: Int): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(field, Document("$arrayElemAt" -> List(BsonString(name), BsonInt32(index))))
    }
    def sum(name: String): ProjectField = {
      ProjectField.Operator(field, Document(name -> Document("$sum" -> 1)))
    }
    def obj(fields: (String, String)*): ProjectField = {
      val projected = MDocument()
      fields.foreach {
        case (key, value) => projected += key -> opKey(value)
      }
      ProjectField.Operator(field, projected.toBsonDocument)
    }
  }

  private def opKey(key: String): String = if (key.startsWith("$")) {
    key
  } else {
    "$" + key
  }
}