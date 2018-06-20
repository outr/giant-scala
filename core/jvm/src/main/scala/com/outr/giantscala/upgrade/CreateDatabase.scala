package com.outr.giantscala.upgrade

import com.outr.giantscala.MongoDatabase

import scala.concurrent.Future
import scribe.Execution.global

object CreateDatabase extends DatabaseUpgrade {
  override def blockStartup: Boolean = false
  override def alwaysRun: Boolean = true
  override def applyToNew: Boolean = true

  override def upgrade(db: MongoDatabase): Future[Unit] = Future.sequence(db.collections.map(_.create())).map(_ => ())
}
