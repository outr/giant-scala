package com.outr.giantscala.upgrade

import com.outr.giantscala.MongoDatabase

import scala.concurrent.Future

trait DatabaseUpgrade {
  def label: String = getClass.getSimpleName.replaceAllLiterally("$", "")
  def applyToNew: Boolean
  def blockStartup: Boolean
  def alwaysRun: Boolean = false

  def upgrade(db: MongoDatabase): Future[Unit]
}