package com.outr.giantscala.dsl

import com.outr.giantscala.Converter
import fabric.rw.RW
import org.mongodb.scala.bson.collection.immutable.Document

case class Count(countResult: Int)

object Count extends Converter[Int] {
  implicit val rw: RW[Count] = RW.gen

  private val count = Converter[Count]
  override def toDocument(t: Int): Document = Document(s"""{"countResult": $t}""")
  override def fromDocument(document: Document): Int = count.fromDocument(document).countResult
}