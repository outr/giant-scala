package com.matthicks.giantscala.oplog

sealed trait OpType

object OpType {
  case object Insert extends OpType
  case object Update extends OpType
  case object Delete extends OpType
  case object Command extends OpType
  case object Noop extends OpType

  def apply(c: Char): OpType = c match {
    case 'i' => Insert
    case 'u' => Update
    case 'd' => Delete
    case 'c' => Command
    case 'n' => Noop
    case _ => throw new RuntimeException(s"Unsupported OpType: $c")
  }
}