package com.matthicks.giantscala

trait ModelObject {
  def _id: String
  def created: Long
  def modified: Long
}