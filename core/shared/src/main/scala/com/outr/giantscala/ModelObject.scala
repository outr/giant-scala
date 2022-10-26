package com.outr.giantscala

trait ModelObject[M <: ModelObject[M]] {
  def _id: Id[M]
}