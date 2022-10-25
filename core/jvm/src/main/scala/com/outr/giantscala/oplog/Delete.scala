package com.outr.giantscala.oplog

import fabric.rw.RW

case class Delete(_id: String) extends AnyVal

object Delete {
  implicit val rw: RW[Delete] = RW.gen
}