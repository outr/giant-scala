package com.outr.giantscala.dsl

import fabric._

case class MatchCondition(json: Json) {
  def or(that: MatchCondition): MatchCondition = {
    MatchCondition(obj("$or" -> arr(this.json, that.json)))
  }

  def ||(that: MatchCondition): MatchCondition = or(that)

  def and(that: MatchCondition): MatchCondition = {
    MatchCondition(obj("$and" -> arr(this.json, that.json)))
  }

  def &&(that: MatchCondition): MatchCondition = and(that)
}