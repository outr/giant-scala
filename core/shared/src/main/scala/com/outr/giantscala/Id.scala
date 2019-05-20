package com.outr.giantscala

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

class Id[T <: ModelObject[T]](val value: String) extends AnyVal

object Id {
  implicit def encoder[T <: ModelObject[T]]: Encoder[Id[T]] = new Encoder[Id[T]] {
    override def apply(a: Id[T]): Json = Json.fromString(a.value)
  }
  implicit def decoder[T <: ModelObject[T]]: Decoder[Id[T]] = new Decoder[Id[T]] {
    override def apply(c: HCursor): Result[Id[T]] = Right(new Id[T](c.value.asString.get))
  }

  def apply[T <: ModelObject[T]](id: String): Id[T] = new Id[T](id)
}