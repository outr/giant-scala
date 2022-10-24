package com.outr.giantscala

import fabric.rw.RW

case class MongoDBServer(host: String = "localhost", port: Int = 27017) {
  override def toString: String = s"$host:$port"
}

object MongoDBServer {
  implicit val rw: RW[MongoDBServer] = RW.gen

  val default: MongoDBServer = MongoDBServer()
}