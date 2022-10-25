package com.outr.giantscala.dsl

import fabric.Json

trait AggregateInstruction extends Implicits {
  def json: Json
}
