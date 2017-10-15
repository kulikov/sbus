package co.uk.vturbo.sbus.model

object Headers {
  val CorrelationId = "correlation-id"
  val MessageId = "message-id"
  val RetryAttemptsMax = "retry-max-attempts"
  val RetryAttemptNr = "retry-attempt-nr"
  val Timeout = "timeout"

  val all = Set(CorrelationId, MessageId, RetryAttemptNr, RetryAttemptsMax, Timeout)
}
