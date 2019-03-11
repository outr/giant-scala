package com.outr.giantscala

case class StoredIndex(v: Int, key: Map[String, Int], name: String, ns: String) {
  lazy val fields: Set[String] = key.keys.toSet
}

object StoredIndex {
  val converter: Converter[StoredIndex] = Converter.auto[StoredIndex]
}