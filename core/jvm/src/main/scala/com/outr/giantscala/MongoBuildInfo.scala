package com.outr.giantscala

import fabric.rw.RW

case class MongoBuildInfo(version: String,
                          gitVersion: String,
                          allocator: String,
                          versionArray: Vector[Int],
                          bits: Int,
                          debug: Boolean,
                          maxBsonObjectSize: Long,
                          ok: Double) {
  lazy val major: Int = versionArray.head
  lazy val minor: Int = versionArray(1)
  lazy val useOplog: Boolean = major < 4
}

object MongoBuildInfo {
  implicit val rw: RW[MongoBuildInfo] = RW.gen
}