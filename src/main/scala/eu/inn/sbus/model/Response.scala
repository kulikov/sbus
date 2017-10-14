package eu.inn.sbus.model

case class Response(
  status: Int,
  body: Option[Any] = None,
  headers: Option[Map[String, String]] = None
)

case class ErrorResponseBody(
  message: String,
  error: Option[String] = None,
  _links: Option[Map[String, Any]] = None
)
