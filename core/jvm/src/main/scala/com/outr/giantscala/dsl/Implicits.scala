package com.outr.giantscala.dsl

import com.outr.giantscala.{Field, Index}
import fabric._
import fabric.rw.{Convertible, RW}

import scala.collection.mutable
import scala.language.implicitConversions

trait Implicits {
  def field[T: RW](name: String): Field[T] = Field[T](name)

  def and(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(obj("$and" -> arr(conditions.map(_.json): _*)))
  }

  def not(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(obj("$not" -> arr(conditions.map(_.json): _*)))
  }

  def nor(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(obj("$nor" -> arr(conditions.map(_.json): _*)))
  }

  def or(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(obj("$or" -> arr(conditions.map(_.json): _*)))
  }

  def expr(condition: MatchCondition): MatchCondition = {
    MatchCondition(obj("$expr" -> condition.json))
  }

  implicit class FieldListExtras[T: RW](f: Field[List[T]]) {
    def ===(value: T): MatchCondition = MatchCondition(obj(f.fieldName -> value.json))
    def isEmpty: MatchCondition = MatchCondition(obj(f.fieldName -> arr()))
    def nonEmpty: MatchCondition = MatchCondition(obj(f.fieldName -> obj("$ne" -> arr())))
    def >(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$gt" -> value.json)))
    }
    def >=(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$gte" -> value.json)))
    }
    def <(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$lt" -> value.json)))
    }
    def <=(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$lte" -> value.json)))
    }
    def in(values: T*): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$in" -> arr(values.map(_.json): _*))))
    }
  }

  implicit class FieldMatching[T](f: Field[T]) {
    implicit def rw: RW[T] = f.rw
    def ===(value: T): MatchCondition = MatchCondition(obj(f.fieldName -> value.json))
    def !==(value: T): MatchCondition = MatchCondition(obj(f.fieldName -> obj("$ne" -> value.json)))
    def ne(value: T): MatchCondition = MatchCondition(obj(f.fieldName -> obj("$ne" -> value.json)))
    def >(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$gt" -> value.json)))
    }
    def >=(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$gte" -> value.json)))
    }
    def <(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$lt" -> value.json)))
    }
    def <=(value: T): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$lte" -> value.json)))
    }
    def equal(value: T): MatchCondition = {
      MatchCondition(obj("$eq" -> arr(str(f.fieldName), value.json)))
    }
    def isNull: MatchCondition = MatchCondition(obj(f.fieldName -> Null))
    def notNull: MatchCondition = MatchCondition(obj(f.fieldName -> obj("$ne" -> Null)))
    def exists: MatchCondition = MatchCondition(obj(f.fieldName -> obj("$exists" -> Bool(true))))
    def mod(modulo: Int, equals: Int): MatchCondition = MatchCondition(obj(f.fieldName -> obj("$mod" -> arr(modulo, equals))))
    def modulus(modulo: Int, equals: Int): MatchCondition = mod(modulo, equals)
    def text(search: String,
             language: Option[String] = None,
             caseSensitive: Boolean = false,
             diacriticSensitive: Boolean = false): MatchCondition = {
      var json: Json = obj("$search" -> str(search))
      language.foreach(l => json = json.merge(obj("$language" -> str(l))))
      if (caseSensitive) json = json.merge(obj("$caseSensitive" -> Bool(true)))
      if (diacriticSensitive) json = json.merge(obj("diacriticSensitive" -> Bool(true)))
      MatchCondition(obj("$text" -> json))
    }
    def where(jsExpression: String): MatchCondition = MatchCondition(obj(f.fieldName -> obj("$where" -> str(jsExpression))))

    /**
      * See https://docs.mongodb.com/manual/reference/operator/query/regex/
      */
    def regex(pattern: String,
              caseInsensitive: Boolean = false,
              multiLine: Boolean = false,
              extended: Boolean = false,
              dotMatchesNewLine: Boolean = false): MatchCondition = {
      val options = new mutable.StringBuilder
      if (caseInsensitive) options.append('i')
      if (multiLine) options.append('m')
      if (extended) options.append('x')
      if (dotMatchesNewLine) options.append('s')
      MatchCondition(obj(f.fieldName -> obj(
        "$regex" -> str(pattern),
        "$options" -> str(options.toString())
      )))
    }

    def in(values: T*): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$in" -> arr(values.map(_.json): _*))))
    }
    def nin(values: T*): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$nin" -> arr(values.map(_.json): _*))))
    }
    def notIn(values: T*): MatchCondition = nin(values: _*)
    def all(values: T*): MatchCondition = {
      MatchCondition(obj("$all" -> arr(values.map(_.json): _*)))
    }
    def size(value: Int): MatchCondition = {
      MatchCondition(obj(f.fieldName -> obj("$size" -> value)))
    }
  }

  implicit class FieldProjection[T](f: Field[T]) {
    implicit def rw: RW[T] = f.rw

    def include: ProjectField = ProjectField.Include(f)
    def exclude: ProjectField = ProjectField.Exclude(f)
    def from[Other](other: Field[Other]): ProjectField = new ProjectField {
      override def json: Json = obj(f.fieldName -> str(s"$$${other.fieldName}"))
    }
    def first[Other](other: Field[Other]): ProjectField = new ProjectField {
      override def json: Json = obj(f.fieldName -> obj("$first" -> str(s"$$${other.fieldName}")))
    }
    def objectToArray(arrayName: String): ProjectField = {
      val name = opKey(arrayName)
      ProjectField.Operator(f, obj("$objectToArray" -> str(name)))
    }
    def addToSet(key: String): ProjectField = {
      ProjectField.Operator(Field(key), obj("$addToSet" -> str(opKey(f.fieldName))))
    }
    def arrayElemAt(key: String, index: Int): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(f, obj("$arrayElemAt" -> arr(str(name), index)))
    }
    def set(value: Json): ProjectField = ProjectField.Operator(f, value)
    def set(value: String): ProjectField = set(str(value))
    def set(fields: ProjectField*): ProjectField = set(fields.json)
    def nullify(): ProjectField = set(Null)

    def key: String = s"${f.fieldName}.k"
    def value: String = s"${f.fieldName}.v"
    def op: String = s"$$${f.fieldName}"

    object index {
      def ascending: Index = Index.Ascending(f)
      def descending: Index = Index.Descending(f)
      def text: Index = Index.Text(f)
    }
  }

  def sum(name: String): ProjectField = new ProjectField {
    override def json: Json = obj(name -> obj("$sum" -> 1))
  }

  implicit class AggregateInstructions(list: Seq[AggregateInstruction]) {
    def json: Json = list.foldLeft[Json](obj())((json, field) => field.json.merge(json))
  }

  implicit def field2String[T](field: Field[T]): String = field.fieldName

  private def opKey(key: String): String = if (key.startsWith("$")) {
    key
  } else {
    "$" + key
  }
}