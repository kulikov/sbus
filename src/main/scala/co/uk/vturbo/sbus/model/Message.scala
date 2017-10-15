package co.uk.vturbo.sbus.model

case class Message(
  routingKey: String,
  body: Option[Any] = None
)
