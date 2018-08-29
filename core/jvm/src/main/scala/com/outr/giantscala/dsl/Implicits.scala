package com.outr.giantscala.dsl

import com.outr.giantscala.{Field, Index}
import io.circe.{Encoder, Json}

import scala.language.implicitConversions

trait Implicits {
  def field[T](name: String): Field[T] = Field[T](name)

  implicit class FieldExtras[T](f: Field[T]) {
    def ===(value: T)
           (implicit encoder: Encoder[T]): MatchCondition = MatchCondition(Json.obj(f.name -> encoder(value)))
    def in(values: T*)
          (implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj("$in" -> Json.arr(values.map(encoder.apply): _*)))
    }
    def include: ProjectField = ProjectField.Include(f)
    def exclude: ProjectField = ProjectField.Exclude(f)
    def objectToArray(arrayName: String): ProjectField = {
      val name = opKey(arrayName)
      ProjectField.Operator(f, Json.obj("$objectToArray" -> Json.fromString(name)))
    }
    def addToSet(key: String): ProjectField = {
      ProjectField.Operator(Field(key), Json.obj("$addToSet" -> Json.fromString(opKey(f.name))))
    }
    def arrayElemAt(key: String, index: Int): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(f, Json.obj("$arrayElemAt" -> Json.arr(Json.fromString(name), Json.fromInt(index))))
    }
    def set(value: Json): ProjectField = ProjectField.Operator(f, value)
    def set(value: String): ProjectField = set(Json.fromString(value))
    def set(fields: ProjectField*): ProjectField = set(fields.json)
    def nullify(): ProjectField = set(Json.Null)

    def key: String = s"${f.name}.k"
    def value: String = s"${f.name}.v"
    def op: String = s"$$${f.name}"

    object index {
      def ascending: Index = Index.Ascending(f.name)
      def descending: Index = Index.Descending(f.name)
      def text: Index = Index.Text(f.name)
    }
  }

  def sum(name: String): ProjectField = new ProjectField {
    override def json: Json = Json.obj(name -> Json.obj("$sum" -> Json.fromInt(1)))
  }

  implicit class AggregateInstructions(list: Seq[AggregateInstruction]) {
    def json: Json = list.foldLeft(Json.obj())((json, field) => field.json.deepMerge(json))
  }

  implicit def field2String[T](field: Field[T]): String = field.name

  private def opKey(key: String): String = if (key.startsWith("$")) {
    key
  } else {
    "$" + key
  }
}