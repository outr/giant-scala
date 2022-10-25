package com.outr.giantscala

import org.reactivestreams.Publisher

trait StreamSupport {
  implicit class PublisherExtras[T](publisher: Publisher[T]) extends Streamable[T, T] {
    override protected def toPublisher: Publisher[T] = publisher

    override protected def convert(t: T): T = t

    def stream: PublisherExtras[T] = this
  }
}