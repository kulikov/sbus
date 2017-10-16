package co.uk.vturbo.sbus.model


class ErrorMessage(
  val code: Int,
  msg: String,
  cause: Throwable = null,
  val error: String = null,
  val _links: java.util.Map[String, Object] = null
) extends RuntimeException(msg, cause)


trait UnrecoverableFailure


class BadRequestError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(400, msg, cause, error) with UnrecoverableFailure {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class UnauthorizedError(msg: String, cause: Throwable = null, error: String = null, val schema: Option[String] = None) extends ErrorMessage(401, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class ForbiddenError(msg: String, cause: Throwable = null, error: String = null, _links: java.util.Map[String, Object] = null) extends ErrorMessage(403, msg, cause, error, _links) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class NotFoundError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(404, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class MethodNotAllowedError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(405, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}


class ConflictError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(409, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class PreconditionFailedError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(412, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class TooManyRequestError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(429, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class InternalServerError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(500, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class ServiceUnavailableError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(503, msg, cause, error) {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}

class UnrecoverableError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(456, msg, cause, error) with UnrecoverableFailure {
  def this(msg: String) = this(msg, null, null)
  def this(msg: String, cause: Throwable) = this(msg, cause, null)
}
