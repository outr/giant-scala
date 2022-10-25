package com.outr.giantscala

import fabric.io.{Format, JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible, RW}
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

  def apply[T](implicit rw: RW[T]): Converter[T] = new Converter[T] {
    override def toDocument(t: T): Document = Document(JsonFormatter.Compact(t.json))

    override def fromDocument(document: Document): T = JsonParser(document.toJson(settings), Format.Json).as[T]
  }

  def apply[T](to: T => String, from: String => T): Converter[T] = new Converter[T] {
    override def toDocument(t: T): Document = Document(to(t))

    override def fromDocument(document: Document): T = from(document.toJson(settings))
  }
}