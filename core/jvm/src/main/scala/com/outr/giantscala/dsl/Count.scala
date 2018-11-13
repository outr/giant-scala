package com.outr.giantscala.dsl

import com.outr.giantscala.Converter
import org.mongodb.scala.bson.collection.immutable.Document

case class Count(countResult: Int)

object Count extends Converter[Int] {
  private val count = Converter.auto[Count]
  override def toDocument(t: Int): Document = Document(s"""{"countResult": $t}""")
  override def fromDocument(document: Document): Int = count.fromDocument(document).countResult
}