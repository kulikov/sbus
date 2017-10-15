package co.uk.vturbo.sbus.javadsl

import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction
import scala.compat.java8.FutureConverters._

import co.uk.vturbo.sbus.model.{Context, Transport}


class Sbus(transport: Transport) {

  def request[T](routingKey: String, responseClass: Class[T]): CompletableFuture[T] =
    request(routingKey, null, responseClass, Context.empty)

  def request[T](routingKey: String, responseClass: Class[T], context: Context): CompletableFuture[T] =
    request(routingKey, null, responseClass, context)

  def request[T](routingKey: String, message: Any, responseClass: Class[T]): CompletableFuture[T] =
    request(routingKey, message, responseClass, Context.empty)

  def request[T](routingKey: String, message: Any, responseClass: Class[T], context: Context): CompletableFuture[T] =
    transport.send(routingKey, message, context, responseClass).toJava.toCompletableFuture.thenApply(_.asInstanceOf[T])

  def command(routingKey: String): CompletableFuture[Void] =
    command(routingKey, null, Context.empty)

  def command(routingKey: String, context: Context): CompletableFuture[Void] =
    command(routingKey, null, context)

  def command(routingKey: String, message: Any): CompletableFuture[Void] =
    command(routingKey, message, Context.empty)

  def command(routingKey: String, message: Any, context: Context): CompletableFuture[Void] =
    transport.send(routingKey, message, context, null).toJava.toCompletableFuture.thenAccept(_ ⇒ {})

  def event(routingKey: String, message: Any): CompletableFuture[Void] =
    event(routingKey, message, Context.empty)

  def event(routingKey: String, message: Any, context: Context): CompletableFuture[Void] =
    transport.send(routingKey, message, context, null).toJava.toCompletableFuture.thenAccept(_ ⇒ {})

  def on[T](routingKey: String, requestClass: Class[T], handler: BiFunction[T, Context, CompletableFuture[_]]) {
    transport.subscribe[T](routingKey, requestClass, { (resp, ctx) ⇒
      handler.apply(resp, ctx).toScala
    })
  }
}
