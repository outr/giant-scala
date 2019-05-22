package com.outr.giantscala

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.bson.types.ObjectId

class Id[T <: ModelObject[T]](val value: String) extends AnyVal {
  override def toString: String = s"Id($value)"
}

object Id {
  def Generate[T <: ModelObject[T]]: Id[T] = new Id[T]("")
  def UUID[T <: ModelObject[T]]: Id[T] = new Id[T](java.util.UUID.randomUUID().toString)

  implicit def encoder[T <: ModelObject[T]]: Encoder[Id[T]] = new Encoder[Id[T]] {
    override def apply(id: Id[T]): Json = if (id.value.isEmpty) {
      Json.fromString(ObjectId.get().toHexString)
    } else {
      Json.fromString(id.value)
    }
  }
  implicit def decoder[T <: ModelObject[T]]: Decoder[Id[T]] = new Decoder[Id[T]] {
    override def apply(c: HCursor): Result[Id[T]] = if (c.value.isNull) {
      Right(Generate[T])
    } else {
      Right(new Id[T](c.value.asString.get))
    }
  }

  def apply[T <: ModelObject[T]](id: String): Id[T] = new Id[T](id)
}