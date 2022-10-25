package com.outr.giantscala

import cats.effect.IO
import cats.effect.kernel.Par.instance.T
import fs2.interop.reactivestreams._
import org.reactivestreams.Publisher

trait Streamable[T, S] {
  protected def toPublisher: Publisher[T]
  protected def convert(t: T): S

  def toStream(bufferSize: Int = 512): fs2.Stream[IO, S] = toPublisher.toStreamBuffered[IO](bufferSize).map(convert)

  def toList: IO[List[S]] = toStream().compile.toList

  def one: IO[S] = toList.map {
    case Nil => throw new RuntimeException("No results")
    case d :: Nil => d
    case list => throw new RuntimeException(s"More than one result returned: $list")
  }

  def first: IO[Option[S]] = toStream().take(1).compile.last

  def last: IO[Option[S]] = toStream().compile.last
}