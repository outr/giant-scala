package com.outr.giantscala

import io.circe.{Decoder, Encoder}
import io.circe.parser._
import org.bson.{BsonTimestamp, json}
import org.bson.json.{JsonWriterSettings, StrictJsonWriter}
import org.mongodb.scala.bson.collection.immutable.Document

import scala.language.experimental.macros

trait Converter[T] {
  def toDocument(t: T): Document
  def fromDocument(document: Document): T
}

object Converter {
  lazy val settings: JsonWriterSettings = json.JsonWriterSettings
    .builder()
    .timestampConverter(new json.Converter[BsonTimestamp] {
      override def convert(value: BsonTimestamp, writer: StrictJsonWriter): Unit = {
        writer.writeString((value.getTime * 1000L).toString)
      }
    })
    .dateTimeConverter(new json.Converter[java.lang.Long] {
      override def convert(value: java.lang.Long, writer: StrictJsonWriter): Unit = {
        writer.writeString(value.toString)
      }
    })
    .int64Converter(new json.Converter[java.lang.Long] {
      override def convert(value: java.lang.Long, writer: json.StrictJsonWriter): Unit = {
        writer.writeString(value.toString)
      }
    }).build()

  def auto[T]: Converter[T] = macro Macros.auto[T]

  def apply[T](decoder: Decoder[T], encoder: Encoder[T]): Converter[T] = new Converter[T] {
    override def toDocument(t: T): Document = Document(encoder(t).noSpaces)

    override def fromDocument(document: Document): T = parse(document.toJson(settings)) match {
      case Left(pf) => throw pf
      case Right(json) => decoder.decodeJson(json) match {
        case Left(pf) => throw pf
        case Right(t) => t
      }
    }
  }

  def apply[T](to: T => String, from: String => T): Converter[T] = new Converter[T] {
    override def toDocument(t: T): Document = Document(to(t))

    override def fromDocument(document: Document): T = from(document.toJson(settings))
  }
}