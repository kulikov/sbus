package ru.kulikovd.sbus.model

case class Message(
  routingKey: String,
  body: Option[Any] = None
)
