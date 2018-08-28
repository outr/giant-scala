package com.outr.giantscala.dsl

import com.outr.giantscala.Field
import io.circe.{Encoder, Json}

trait Implicits {
  implicit class FieldExtras[T](field: Field[T]) {
    def ===(value: T)
           (implicit encoder: Encoder[T]): MatchCondition = MatchCondition(Json.obj(field.name -> encoder(value)))
    def in(values: T*)
          (implicit encoder: Encoder[T]): MatchCondition = {
      MatchCondition(Json.obj("$in" -> Json.arr(values.map(encoder.apply): _*)))
    }
    def include: ProjectField = ProjectField.Include(field)
    def exclude: ProjectField = ProjectField.Exclude(field)
    def objectToArray(arrayName: String): ProjectField = {
      val name = opKey(arrayName)
      ProjectField.Operator(field, Json.obj("$objectToArray" -> Json.fromString(name)))
    }
    def addToSet(key: String): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(field, Json.obj("$addToSet" -> Json.fromString(name)))
    }
    def arrayElemAt(key: String, index: Int): ProjectField = {
      val name = opKey(key)
      ProjectField.Operator(field, Json.obj("$arrayElemAt" -> Json.arr(Json.fromString(name), Json.fromInt(index))))
    }
    def sum(name: String): ProjectField = {
      ProjectField.Operator(field, Json.obj(name -> Json.obj("$sum" -> Json.fromInt(1))))
    }
    def obj(fields: (String, String)*): ProjectField = {
      val projected = Json.obj(fields.map {
        case (key, value) => key -> Json.fromString(opKey(value))
      }: _*)
      ProjectField.Operator(field, projected)
    }
  }

  private def opKey(key: String): String = if (key.startsWith("$")) {
    key
  } else {
    "$" + key
  }
}