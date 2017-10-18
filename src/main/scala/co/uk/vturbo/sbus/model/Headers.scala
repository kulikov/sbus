package co.uk.vturbo.sbus.model

object Headers {
  val CorrelationId = "correlation-id"
  val MessageId = "message-id"
  val ClientMessageId = "client-message-id"
  val RetryAttemptsMax = "retry-max-attempts"
  val RetryAttemptNr = "retry-attempt-nr"
  val Timeout = "timeout"
  val RoutingKey = "routing-key"

  val all = Set(CorrelationId, MessageId, RetryAttemptNr, RetryAttemptsMax, Timeout)
}
