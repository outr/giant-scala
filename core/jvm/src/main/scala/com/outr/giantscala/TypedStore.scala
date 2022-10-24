package com.outr.giantscala

import scala.concurrent.Future

trait TypedStore[T] {
  def get: Future[Option[T]]
  def apply(default: => T): Future[T]
  def set(value: T): Future[Unit]
}