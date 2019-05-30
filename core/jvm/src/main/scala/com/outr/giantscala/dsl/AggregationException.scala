package com.outr.giantscala.dsl

case class AggregationException(query: String, cause: Throwable) extends RuntimeException(s"Aggregation Exception on $query", cause)