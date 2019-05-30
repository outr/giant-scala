package com.outr.giantscala.dsl

import com.outr.giantscala.{Field, Index}
import io.circe.{Encoder, Json}

import scala.language.implicitConversions

trait Implicits {
  def field[T](name: String): Field[T] = Field[T](name)

  def and(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(Json.obj("$and" -> Json.fromValues(conditions.map(_.json))))
  }

  def not(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(Json.obj("$not" -> Json.fromValues(conditions.map(_.json))))
  }

  def nor(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(Json.obj("$nor" -> Json.fromValues(conditions.map(_.json))))
  }

  def or(conditions: MatchCondition*): MatchCondition = {
    MatchCondition(Json.obj("$or" -> Json.fromValues(conditions.map(_.json))))
  }

  def expr(condition: MatchCondition): MatchCondition = {
    MatchCondition(Json.obj("$expr" -> condition.json))
  }

  implicit class FieldListExtras[T](f: Field[List[T]]) {
    def ===(value: T)(implicit encoder: Encoder[T]): MatchCondition = MatchCondition(Json.obj(f.fieldName -> encoder(value)))
    def >(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$gt" -> encoder(value))))
    }
    def >=(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$gte" -> encoder(value))))
    }
    def <(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$lt" -> encoder(value))))
    }
    def <=(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$lte" -> encoder(value))))
    }
  }

  implicit class FieldMatching[T](f: Field[T]) {
    def ===(value: T)
           (implicit encoder: Encoder[T]): MatchCondition = MatchCondition(Json.obj(f.fieldName -> encoder(value)))
    def !==(value: T)
           (implicit encoder: Encoder[T]): MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.obj("$ne" -> encoder(value))))
    def ne(value: T)
          (implicit encoder: Encoder[T]): MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.obj("$ne" -> encoder(value))))
    def >(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$gt" -> encoder(value))))
    }
    def >=(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$gte" -> encoder(value))))
    }
    def <(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$lt" -> encoder(value))))
    }
    def <=(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$lte" -> encoder(value))))
    }
    def equal(value: T)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj("$eq" -> Json.arr(Json.fromString(f.fieldName), encoder(value))))
    }
    def isNull: MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.Null))
    def notNull: MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.obj("$ne" -> Json.Null)))
    def exists: MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.obj("$exists" -> Json.True)))
    def mod(modulo: Int, equals: Int): MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.obj("$mod" -> Json.arr(Json.fromInt(modulo), Json.fromInt(equals)))))
    def modulus(modulo: Int, equals: Int): MatchCondition = mod(modulo, equals)
    def text(search: String,
             language: Option[String] = None,
             caseSensitive: Boolean = false,
             diacriticSensitive: Boolean = false): MatchCondition = {
      var json = Json.obj("$search" -> Json.fromString(search))
      language.foreach(l => json = json.deepMerge(Json.obj("$language" -> Json.fromString(l))))
      if (caseSensitive) json = json.deepMerge(Json.obj("$caseSensitive" -> Json.True))
      if (diacriticSensitive) json = json.deepMerge(Json.obj("diacriticSensitive" -> Json.True))
      MatchCondition(Json.obj("$text" -> json))
    }
    def where(jsExpression: String): MatchCondition = MatchCondition(Json.obj(f.fieldName -> Json.obj("$where" -> Json.fromString(jsExpression))))

    /**
      * See https://docs.mongodb.com/manual/reference/operator/query/regex/
      */
    def regex(pattern: String,
              caseInsensitive: Boolean = false,
              multiLine: Boolean = false,
              extended: Boolean = false,
              dotMatchesNewLine: Boolean = false): MatchCondition = {
      val options = new StringBuilder
      if (caseInsensitive) options.append('i')
      if (multiLine) options.append('m')
      if (extended) options.append('x')
      if (dotMatchesNewLine) options.append('s')
      MatchCondition(Json.obj(f.fieldName -> Json.obj(
        "$regex" -> Json.fromString(pattern),
        "$options" -> Json.fromString(options.toString())
      )))
    }

    def in(values: T*)
          (implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$in" -> Json.arr(values.map(encoder.apply): _*))))
    }
    def nin(values: T*)
           (implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$nin" -> Json.arr(values.map(encoder.apply): _*))))
    }
    def notIn(values: T*)(implicit encoder: Encoder[T]): MatchCondition = nin(values: _*)(encoder)
    def all(values: T*)(implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj("$all" -> Json.arr(values.map(encoder.apply): _*)))
    }
    def size(value: Int): MatchCondition = {
      MatchCondition(Json.obj(f.fieldName -> Json.obj("$size" -> Json.fromInt(value))))
    }
  }

  implicit class FieldProjection[T](f: Field[T]) {
    def include: ProjectField = ProjectField.Include(f)
    def exclude: ProjectField = ProjectField.Exclude(f)
    def from[Other](other: Field[Other]): ProjectField = new ProjectField {
      override def json: Json = Json.obj(f.fieldName -> Json.fromString(s"$$${other.fieldName}"))
    }
    def first[Other](other: Field[Other]): ProjectField = new ProjectField {
      override def json: Json = Json.obj(f.fieldName -> Json.obj("$first" -> Json.fromString(s"$$${other.fieldName}")))
    }
    def objectToArray(arrayName: String): ProjectField = {
      val name = opKey(arrayName)
      ProjectField.Operator(f, Json.obj("$objectToArray" -> Json.fromString(name)))
    }
    def addToSet(key: String): ProjectField = {
      ProjectField.Operator(Field(key), Json.obj("$addToSet" -> Json.fromString(opKey(f.fieldName))))
    }
    def arrayElemAt(key: String, index: Int): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(f, Json.obj("$arrayElemAt" -> Json.arr(Json.fromString(name), Json.fromInt(index))))
    }
    def set(value: Json): ProjectField = ProjectField.Operator(f, value)
    def set(value: String): ProjectField = set(Json.fromString(value))
    def set(fields: ProjectField*): ProjectField = set(fields.json)
    def nullify(): ProjectField = set(Json.Null)

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
    override def json: Json = Json.obj(name -> Json.obj("$sum" -> Json.fromInt(1)))
  }

  implicit class AggregateInstructions(list: Seq[AggregateInstruction]) {
    def json: Json = list.foldLeft(Json.obj())((json, field) => field.json.deepMerge(json))
  }

  implicit def field2String[T](field: Field[T]): String = field.fieldName

  private def opKey(key: String): String = if (key.startsWith("$")) {
    key
  } else {
    "$" + key
  }
}