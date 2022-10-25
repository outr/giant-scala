package com.outr.giantscala

import fabric.rw.RW

case class Credentials(username: String, password: String, authenticationDatabase: String = "admin")

object Credentials {
  implicit val rw: RW[Credentials] = RW.gen
}