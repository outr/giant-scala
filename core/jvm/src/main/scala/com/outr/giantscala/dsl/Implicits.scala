package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import io.circe.{Encoder, Json}

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
    def sum: ProjectField = new ProjectField {
      override def json: Json = Json.obj(f.name -> Json.obj("$sum" -> Json.fromInt(1)))
    }
    def sum(name: String): ProjectField = {
      ProjectField.Operator(f, Json.obj(name -> Json.obj("$sum" -> Json.fromInt(1))))
    }
    def set(value: Json): ProjectField = ProjectField.Operator(f, value)
    def set(value: String): ProjectField = set(Json.fromString(value))
    def set(fields: ProjectField*): ProjectField = set(fields.json)
    def nullify(): ProjectField = set(Json.Null)
  }

  implicit class AggregateInstructions(list: Seq[AggregateInstruction]) {
    def json: Json = list.foldLeft(Json.obj())((json, field) => field.json.deepMerge(json))
  }

  private def opKey(key: String): String = if (key.startsWith("$")) {
    key
  } else {
    "$" + key
  }
}