package com.outr.giantscala.dsl

import org.mongodb.scala.bson.conversions.Bson

case class MatchCondition(bson: Bson)
