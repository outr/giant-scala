package com.outr.giantscala.upgrade

import cats.effect.IO
import com.outr.giantscala.MongoDatabase

trait DatabaseUpgrade {
  def label: String = getClass.getSimpleName.replace("$", "")
  def applyToNew: Boolean
  def blockStartup: Boolean
  def alwaysRun: Boolean = false

  def upgrade(db: MongoDatabase): IO[Unit]
}