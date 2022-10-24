package com.outr.giantscala

import fabric.rw.RW

case class StoredIndex(v: Int, key: Map[String, Int], name: String, ns: Option[String]) {
  lazy val fields: Set[String] = key.keys.toSet
}

object StoredIndex {
  implicit val rw: RW[StoredIndex] = RW.gen

  val converter: Converter[StoredIndex] = Converter[StoredIndex]
}