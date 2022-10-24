package com.outr.giantscala

import fabric.rw.RW

case class MongoBuildInfo(version: String,
                          gitVersion: String,
                          allocator: String,
                          versionArray: Vector[Int],
                          bits: Int,
                          debug: Boolean,
                          maxBsonObjectSize: Long,
                          ok: Double)

object MongoBuildInfo {
  implicit val rw: RW[MongoBuildInfo] = RW.gen
}