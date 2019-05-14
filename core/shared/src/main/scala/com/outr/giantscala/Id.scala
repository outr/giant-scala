package com.outr.giantscala

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

class Id[T <: ModelObject[T]](val value: String) extends AnyVal {
  private def split: Int = value.indexOf('/')
  def collection: String = if (Id.IncludeCollection) {
    value.substring(0, split)
  } else {
    ""
  }
  def id: String = if (Id.IncludeCollection) {
    value.substring(split + 1)
  } else {
    value
  }
}

object Id {
  var IncludeCollection: Boolean = true

  implicit def encoder[T <: ModelObject[T]]: Encoder[Id[T]] = new Encoder[Id[T]] {
    override def apply(a: Id[T]): Json = Json.fromString(a.value)
  }
  implicit def decoder[T <: ModelObject[T]]: Decoder[Id[T]] = new Decoder[Id[T]] {
    override def apply(c: HCursor): Result[Id[T]] = Right(new Id[T](c.value.asString.get))
  }

  def apply[T <: ModelObject[T]](collection: String, id: String): Id[T] = if (IncludeCollection) {
    new Id[T](s"$collection/$id")
  } else {
    new Id[T](id)
  }
}