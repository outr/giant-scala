package com.outr.giantscala

import cats.effect.IO

trait TypedStore[T] {
  def get: IO[Option[T]]
  def apply(default: => T): IO[T]
  def set(value: T): IO[Unit]
}