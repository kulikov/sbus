package eu.inn.sbus.model

import scala.concurrent.Future


trait Transport {

  def send(routingKey: String, msg: Any, context: Context, responseClass: Class[_]): Future[Any]

  def subscribe[T](routingKey: String, messageClass: Class[_], handler: (T, Context) ⇒ Future[Any]): Unit
}
