package com.outr.giantscala.upgrade

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.outr.giantscala.MongoDatabase

object CreateDatabase extends DatabaseUpgrade {
  override def blockStartup: Boolean = false
  override def alwaysRun: Boolean = true
  override def applyToNew: Boolean = true

  override def upgrade(db: MongoDatabase): IO[Unit] = {
    val createIndexes = db.collections.map(_.create())
    createIndexes.toList.sequence.map(_ => ())
  }
}