package co.uk.vturbo.sbus

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

import co.uk.vturbo.sbus.model.Transport

import co.uk.vturbo.sbus.model.{Context, Transport}


class Sbus(transport: Transport)(implicit ec: ExecutionContext) {

  def request[T](routingKey: String, msg: Any = null)(implicit context: Context = Context.empty, tag: ClassTag[T]): Future[T] =
    transport.send(routingKey, msg, context, tag.runtimeClass).mapTo[T]

  def command(routingKey: String, msg: Any = null)(implicit context: Context = Context.empty): Future[Unit] =
    transport.send(routingKey, msg, context, null).map(_ ⇒ {})

  def event(routingKey: String, msg: Any)(implicit context: Context = Context.empty): Future[Unit] =
    command(routingKey, msg)

  def on[T, R](routingKey: String)(handler: (T, Context) ⇒ Future[R])(implicit tag: ClassTag[T]): Unit =
    transport.subscribe[T](routingKey, tag.runtimeClass, handler)
}
