package eu.inn.sbus.model

case class Message(
  routingKey: String,
  body: Option[Any] = None
)
