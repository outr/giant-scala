package com.outr.giantscala

case class MongoBuildInfo(version: String,
                          gitVersion: String,
                          allocator: String,
                          versionArray: Vector[Int],
                          bits: Int,
                          debug: Boolean,
                          maxBsonObjectSize: Long,
                          ok: Double)