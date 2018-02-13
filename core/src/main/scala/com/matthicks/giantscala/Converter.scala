package com.matthicks.giantscala

import org.mongodb.scala.bson.collection.immutable.Document

import scala.language.experimental.macros

trait Converter[T] {
  def toDocument(t: T): Document
  def fromDocument(document: Document): T
}

object Converter {
  def auto[T]: Converter[T] = macro Macros.auto[T]
}