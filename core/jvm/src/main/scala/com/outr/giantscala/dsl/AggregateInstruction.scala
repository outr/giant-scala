package com.outr.giantscala.dsl

import io.circe.Json

trait AggregateInstruction {
  def json: Json
}
