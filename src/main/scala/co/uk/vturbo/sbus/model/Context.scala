package co.uk.vturbo.sbus.model

import scala.collection.JavaConverters._
import scala.concurrent.duration.TimeUnit

import akka.util.Timeout
import com.github.sstone.amqp.Amqp


case class Context(data: Map[String, Any] = Map.empty) {
  def get(key: String) = data.get(key)

  def timeout = get(Headers.Timeout).map(_.toString.toLong)
  def maxRetries = get(Headers.RetryAttemptsMax).map(_.toString.toInt)
  def attemptNr = get(Headers.RetryAttemptNr).fold(1)(_.toString.toInt)
  def correlationId = get(Headers.CorrelationId).map(_.toString)

  def withCorrelationId(id: String) = copy(data = data + (Headers.CorrelationId → id))

  def withTimeout(to: Timeout): Context = withTimeout(to.duration.toMillis)
  def withTimeout(value: Long, unit: TimeUnit): Context = withTimeout(Timeout(value, unit))
  def withTimeout(millis: Long): Context = copy(data = data + (Headers.Timeout → millis))

  def withRetries(max: Int) = copy(data = data + (Headers.RetryAttemptsMax → max))
}


object Context {
  private val emptyContext = Context()

  def empty = emptyContext
  def withCorrelationId(id: String) = Context().withCorrelationId(id)

  def withTimeout(to: Timeout): Context = Context().withTimeout(to)
  def withTimeout(value: Long, unit: TimeUnit): Context = Context().withTimeout(value, unit)
  def withTimeout(millis: Long): Context = Context().withTimeout(millis)

  def withRetries(max: Int) = Context().withRetries(max)

  def from(delivery: Amqp.Delivery) = {
    if (delivery.properties.getHeaders == null) {
      Context.empty
    } else {
      val heads = delivery.properties.getHeaders.asScala.filterKeys(Headers.all).mapValues(_.toString).toMap

      Context(heads ++ Map(
        Headers.MessageId → delivery.properties.getMessageId
      ))
    }
  }
}
