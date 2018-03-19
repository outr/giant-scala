package com.outr.giantscala.failure

import org.mongodb.scala.MongoException

case class DBFailure(`type`: FailureType, throwable: Throwable)

object DBFailure {
  def apply(exc: MongoException): DBFailure = DBFailure(FailureType(exc.getCode), exc)
}

sealed abstract class FailureType(val code: Int)

object FailureType {
  case object SocketException extends FailureType(9001)
  case object NotMaster extends FailureType(10107)
  case object CannotGrowDocumentInCappedNamespace extends FailureType(10003)
  case object DuplicateKey extends FailureType(11000)
  case object InterruptedAtShutdown extends FailureType(11600)
  case object Interrupted extends FailureType(11601)
  case object OutOfDiskSpace extends FailureType(14031)
  case object KeyTooLong extends FailureType(17280)
  case object BackgroundOperationInProgressForDatabase extends FailureType(12586)
  case object BackgroundOperationInProgressForNamespace extends FailureType(12587)
  case object NotMasterOrSecondary extends FailureType(13436)
  case object NotMasterNoSlaveOk extends FailureType(13435)
  case object ShardKeyTooBig extends FailureType(13334)
  case object StaleConfig extends FailureType(13388)
  case object DatabaseDifferCase extends FailureType(13297)

  case object Unknown extends FailureType(-1)

  lazy val all: List[FailureType] = List(
    SocketException, NotMaster, CannotGrowDocumentInCappedNamespace, DuplicateKey, InterruptedAtShutdown, Interrupted,
    OutOfDiskSpace, KeyTooLong, BackgroundOperationInProgressForDatabase, BackgroundOperationInProgressForNamespace,
    NotMasterOrSecondary, NotMasterNoSlaveOk, ShardKeyTooBig, StaleConfig, DatabaseDifferCase
  )
  private lazy val map: Map[Int, FailureType] = Map(all.map(ft => ft.code -> ft): _*)

  def get(code: Int): Option[FailureType] = map.get(code)
  def apply(code: Int): FailureType = get(code).getOrElse(Unknown)
}