package com.outr.giantscala.dsl

import com.outr.giantscala._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros

case class AggregateBuilder[Type <: ModelObject, Out](collection: DBCollection[Type],
                                                      converter: Converter[Out],
                                                      pipeline: List[AggregateInstruction] = Nil) {
  def withPipeline(instructions: AggregateInstruction*): AggregateBuilder[Type, Out] = {
    copy(pipeline = pipeline ::: instructions.toList)
  }
  def `match`(conditions: MatchCondition*): AggregateBuilder[Type, Out] = {
    withPipeline(AggregateMatch(conditions.toList))
  }
  def project(fields: Field[_]): AggregateBuilder[Type, Out] = {
    withPipeline()
  }

  def as[T](converter: Converter[T]): AggregateBuilder[Type, T] = copy(converter = converter)
  def as[T]: AggregateBuilder[Type, T] = macro Macros.aggregateAs[T]

  def toFuture(implicit executionContext: ExecutionContext): Future[List[Out]] = {
    collection.collection.aggregate(pipeline.map(_.bson)).toFuture().map(_.map(converter.fromDocument).toList)
  }
}