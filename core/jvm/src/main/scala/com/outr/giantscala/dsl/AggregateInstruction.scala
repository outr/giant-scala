package com.outr.giantscala.dsl

import io.circe.Json

trait AggregateInstruction extends Implicits {
  def json: Json
}
