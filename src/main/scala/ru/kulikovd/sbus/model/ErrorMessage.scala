package ru.kulikovd.sbus.model


class ErrorMessage(val code: Int, msg: String, cause: Throwable = null, val error: String = null, val _links: Option[Map[String, Any]] = None)
    extends RuntimeException(msg, cause)

trait UnrecoverableFailure

class BadRequestError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(400, msg, cause, error) with UnrecoverableFailure
class UnauthorizedError(msg: String, cause: Throwable = null, error: String = null, val schema: Option[String] = None) extends ErrorMessage(401, msg, cause, error)
class ForbiddenError(msg: String, cause: Throwable = null, error: String = null, _links: Option[Map[String, Any]] = None) extends ErrorMessage(403, msg, cause, error, _links)
class NotFoundError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(404, msg, cause, error)
class MethodNotAllowedError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(405, msg, cause, error)
class ConflictError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(409, msg, cause, error)
class PreconditionFailedError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(412, msg, cause, error)
class TooManyRequestError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(429, msg, cause, error)
class InternalServerError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(500, msg, cause, error)
class ServiceUnavailableError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(503, msg, cause, error)
class UnrecoverableError(msg: String, cause: Throwable = null, error: String = null) extends ErrorMessage(456, msg, cause, error) with UnrecoverableFailure
