package ru.kulikovd.sbus.model

import scala.collection.JavaConverters._

import akka.util.Timeout
import com.github.sstone.amqp.Amqp


case class Context(data: Map[String, Any] = Map.empty) {
  def get(key: String) = data.get(key)

  def timeout = get(Headers.Timeout).map(_.toString.toLong)
  def maxRetries = get(Headers.RetryAttemptsMax).map(_.toString.toInt)
  def attemptNr = get(Headers.RetryAttemptNr).fold(1)(_.toString.toInt)
  def correlationId = get(Headers.CorrelationId).map(_.toString)

  def withCorrelationId(id: String) = copy(data = data + (Headers.CorrelationId → id))
  def withTimeout(millis: Long) = copy(data = data + (Headers.Timeout → millis))
  def withTimeout(to: Timeout) = copy(data = data + (Headers.Timeout → to.duration.toMillis))
  def withMaxRetries(max: Int) = copy(data = data + (Headers.RetryAttemptsMax → max))
}


object Context {
  private val emptyContext = Context()

  def empty = emptyContext
  def withCorrelationId(id: String) = Context().withCorrelationId(id)
  def withTimeout(millis: Long) = Context().withTimeout(millis)
  def withTimeout(to: Timeout) = Context().withTimeout(to)
  def withMaxRetries(max: Int) = Context().withMaxRetries(max)

  def from(delivery: Amqp.Delivery) = {
    val heads = delivery.properties.getHeaders.asScala.filterKeys(Headers.all).mapValues(_.toString).toMap

    Context(heads ++ Map(
      Headers.MessageId → delivery.properties.getMessageId
    ))
  }
}
