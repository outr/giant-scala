package com.outr.giantscala.dsl

import io.circe.Json

case class MatchCondition(json: Json) {
  def or(that: MatchCondition): MatchCondition = {
    MatchCondition(Json.obj("$or" -> Json.fromValues(List(this.json, that.json))))
  }

  def ||(that: MatchCondition): MatchCondition = or(that)

  def and(that: MatchCondition): MatchCondition = {
    MatchCondition(Json.obj("$and" -> Json.fromValues(List(this.json, that.json))))
  }

  def &&(that: MatchCondition): MatchCondition = and(that)
}