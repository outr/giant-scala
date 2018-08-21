package com.outr.giantscala.dsl

import org.mongodb.scala.bson.conversions.Bson

trait AggregateInstruction {
  def bson: Bson
}
