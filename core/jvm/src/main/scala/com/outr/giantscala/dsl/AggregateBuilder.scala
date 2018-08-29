package com.outr.giantscala.dsl

import com.outr.giantscala._
import io.circe.{Json, Printer}
import org.mongodb.scala.bson.collection.immutable.Document

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
  def project(fields: ProjectField*): AggregateBuilder[Type, Out] = withPipeline(AggregateProject(fields.toList))
  def group(fields: ProjectField*): AggregateBuilder[Type, Out] = withPipeline(AggregateGroup(fields.toList))
  def sample(size: Int): AggregateBuilder[Type, Out] = withPipeline(AggregateSample(size))

  def as[T](converter: Converter[T]): AggregateBuilder[Type, T] = copy(converter = converter)
  def as[T]: AggregateBuilder[Type, T] = macro Macros.aggregateAs[T]

  def json: List[Json] = pipeline.map(_.json)
  def jsonStrings: List[String] = json.map(_.pretty(Printer.spaces2))
  def documents: List[Document] = jsonStrings.map(Document.apply)

  def toQuery(includeSpaces: Boolean = true): String = {
    val printer = if (includeSpaces) {
      Printer.spaces2
    } else {
      Printer.noSpaces
    }
    s"db.${collection.collectionName}.aggregate(${Json.arr(json: _*).pretty(printer)})"
  }
  def toFuture(implicit executionContext: ExecutionContext): Future[List[Out]] = {
    collection.collection.aggregate(documents).toFuture().map(_.map(converter.fromDocument).toList)
  }
}