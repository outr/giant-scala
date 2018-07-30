package com.outr.giantscala

case class MongoDBServer(host: String = "localhost", port: Int = 27017) {
  override def toString: String = s"$host:$port"
}

object MongoDBServer {
  val default: MongoDBServer = MongoDBServer()
}