package com.matthicks.giantscala

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
}