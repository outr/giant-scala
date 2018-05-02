package com.outr.giantscala.oplog

import io.circe.Json

/**
  * Record entry from OperationsLog
  *
  * See: http://dbversity.com/mongodb-understanding-oplog/
  *
  * @param ts time of the operation
  * @param t unknown
  * @param h unique id hash for each operation
  * @param v version
  * @param op operation type:
  *           i: insert
  *           d: delete
  *           u: update
  *           c: command operation
  *           n: noop
  * @param ns the database and collection affected by the operation (db.collection)
  * @param wall wall clock time
  * @param o operation data
  */
case class Operation(ts: Long,
                     t: Int,
                     h: Long,
                     v: Int,
                     op: Char,
                     ns: String,
                     wall: Long,
                     o: Json) {
  lazy val `type`: OpType = OpType(op)
}