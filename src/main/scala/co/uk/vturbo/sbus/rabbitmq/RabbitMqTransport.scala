package co.uk.vturbo.sbus.rabbitmq

import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.sstone.amqp._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.{LoggerFactory, MDC}

import co.uk.vturbo.sbus.model._


class RabbitMqTransport(conf: Config, actorSystem: ActorSystem, mapper: ObjectMapper) extends Transport {

  implicit val defaultTimeout = Timeout(12.seconds)
  implicit val ec = actorSystem.dispatcher

  private val log = Logger(LoggerFactory.getLogger("sbus.rabbitmq"))

  private val connection = actorSystem.actorOf(ConnectionOwner.props({
    log.debug("Sbus connecting to: " + conf.getString("host"))

    val cf = new ConnectionFactory()
    cf.setHost(conf.getString("host"))
    cf.setPort(conf.getInt("port"))
    cf
  }, 3.seconds), name = "rabbitmq-connection")

  private val ChannelParams  = Amqp.ChannelParameters(qos = conf.getInt("prefetch-count"), global = false)
  private val CommonExchange = Amqp.ExchangeParameters(conf.getString("exchange"), passive = false, exchangeType = "direct")
  private val RetryExchange  = Amqp.ExchangeParameters(conf.getString("retry-exchange"), passive = false, exchangeType = "fanout")

  private val producer = {
    val child = ConnectionOwner.createChildActor(connection, ChannelOwner.props())
    Amqp.waitForConnection(actorSystem, child).await()

    for {
      _ ← child ? Amqp.DeclareExchange(CommonExchange)
      _ ← child ? Amqp.DeclareExchange(RetryExchange)

      _ ← child ? Amqp.DeclareQueue(
        Amqp.QueueParameters("retries", passive = false, durable = true, exclusive = false, autodelete = false, args = Map("x-dead-letter-exchange" → CommonExchange.name)))

      _ ← child ? Amqp.QueueBind("retries", RetryExchange.name, "#")
    } {}

    child
  }

  private val rpcClient = {
    val child = ConnectionOwner.createChildActor(connection, RpcClient.props(Some(ChannelParams)))
    Amqp.waitForConnection(actorSystem, child).await()
    child
  }

  /**
   */
  def send(routingKey: String, msg: Any, context: Context, responseClass: Class[_]): Future[Any] = {
    val bytes = mapper.writeValueAsBytes(Message(routingKey = routingKey, body = Option(msg)))

    val corrId = context.correlationId.getOrElse(UUID.randomUUID().toString)

    val propsBldr = new BasicProperties().builder()
      .deliveryMode(if (responseClass != null) 1 else 2)   // 2 → persistent
      .messageId(UUID.randomUUID().toString)
      .headers(Map(
        Headers.CorrelationId    → corrId,
        Headers.RetryAttemptsMax → context.maxRetries.getOrElse(0)
      ).mapValues(_.toString.asInstanceOf[Object]).asJava)

    context.timeout foreach { timeoutMs ⇒
      propsBldr.expiration(timeoutMs.toString)
    }

    val props = propsBldr.build()

    logs("~~~>", routingKey, bytes, corrId)

    val pub = Amqp.Publish(CommonExchange.name, routingKey, bytes, Some(props))

    (if (responseClass != null) {
      rpcClient.ask(RpcClient.Request(pub))(context.timeout.fold(defaultTimeout)(_.millis)) map {
        case RpcClient.Response(deliveries) ⇒
          logs("resp <~~~", routingKey, deliveries.head.body, corrId)

          val response = mapper.readValue(deliveries.head.body, classOf[Response])

          if (response.status < 400)  {
            mapper.convertValue(response.body, responseClass)
          } else {
            val err = mapper.convertValue(response.body.orNull, classOf[ErrorResponseBody])
            throw new ErrorMessage(response.status, err.message, error = err.error.orNull, _links = err._links)
          }

        case other ⇒
          throw new ErrorMessage(500, "Unexpected request error " + routingKey + ":" + other)
      }
    } else {
      producer ? pub map {
        case _: Amqp.Ok ⇒ // ok
        case error ⇒ throw new ErrorMessage(500, "Error on publish message to " + routingKey + ": " + error)
      }
    }) recover {
      case e: AskTimeoutException ⇒
        logs("timeout error", routingKey, bytes, corrId)
        throw new ErrorMessage(406, s"Timeout on `$routingKey` with message: $msg", e)

      case e: Throwable ⇒
        logs("error", routingKey, bytes, corrId)
        throw e
    }
  }

  /**
   */
  def subscribe[T](routingKey: String, messageClass: Class[_], handler: (T, Context) ⇒ Future[Any]): Unit = {
    val processor = new RpcServer.IProcessor {

      def process(delivery: Amqp.Delivery): Future[RpcServer.ProcessResult] = {
        (try {
          logs("<~~~", routingKey, delivery.body, getCorrelationId(delivery))

          val payload = (mapper.readTree(delivery.body).get("body") match {
            case null ⇒ null
            case body ⇒ mapper.treeToValue(body, messageClass)
          }).asInstanceOf[T]

          (try handler(payload, Context.from(delivery)) catch {
            case e: Throwable ⇒ Future.failed(e)
          }) map {
            case null | Unit                 ⇒ RpcServer.ProcessResult(None)
            case re if re.isInstanceOf[Void] ⇒ RpcServer.ProcessResult(None)

            case result ⇒
              val bytes = mapper.writeValueAsBytes(Response(status = 200, body = Some(result)))
              logs("resp ~~~>", routingKey, bytes, getCorrelationId(delivery))
              RpcServer.ProcessResult(Some(bytes))

          } recoverWith {
            case e: Throwable if !e.isInstanceOf[UnrecoverableFailure] ⇒
              val heads       = delivery.properties.getHeaders
              val attemptsMax = Option(heads.get(Headers.RetryAttemptsMax)).map(_.toString.toInt)
              val attemptNr   = Option(heads.get(Headers.RetryAttemptNr)).fold(1)(_.toString.toInt)

              if (attemptsMax.exists(_ >= attemptNr)) {
                heads.put(Headers.RetryAttemptNr, s"${attemptNr + 1}")

                val updProps = delivery.properties.builder()
                  .headers(heads)
                  .expiration(s"${math.pow(2, math.min(attemptNr - 1, 7)).round * 1000}") // millis, exponential backoff
                  .build()

                logs("error", routingKey, s"$e. Retry attempt ${attemptNr + 1} after ${updProps.getExpiration} millis...".getBytes, getCorrelationId(delivery), e)

                producer ? Amqp.Publish(RetryExchange.name, routingKey, delivery.body, Some(updProps), mandatory = false) map {
                  case _: Amqp.Ok ⇒ RpcServer.ProcessResult(None)
                  case error      ⇒ throw new ErrorMessage(500, "Error on publish retry message for " + routingKey + ": " + error)
                }
              } else {
                Future.failed(e)
              }
          }
        } catch {
          case e: Throwable ⇒ Future.failed(e)
        }) recover {
          case e: Throwable ⇒ onFailure(delivery, e)
        }
      }

      def onFailure(delivery: Amqp.Delivery, e: Throwable): RpcServer.ProcessResult = {
        logs("error", routingKey, e.toString.getBytes, getCorrelationId(delivery), e)

        if (delivery.properties.getReplyTo != null) {
          val response = e match {
            case em: ErrorMessage ⇒
              Response(status = em.code, body = Some(ErrorResponseBody(message = em.getMessage, error = Option(em.error), _links = em._links)))

            case _ ⇒
              Response(status = 500, body = Some(ErrorResponseBody(message = e.getMessage)))
          }

          val bytes = mapper.writeValueAsBytes(response)
          logs("resp ~~~>", routingKey, bytes, getCorrelationId(delivery))
          RpcServer.ProcessResult(Some(bytes))
        } else {
          RpcServer.ProcessResult(None)
        }
      }
    }

    val child = ConnectionOwner.createChildActor(connection, RpcServer.props(
      queue         = Amqp.QueueParameters(routingKey, passive = false, durable = false, exclusive = false, autodelete = false),
      exchange      = CommonExchange,
      routingKey    = routingKey,
      proc          = processor,
      channelParams = ChannelParams
    ))

    log.debug("Sbus subscribed to: " + routingKey)

    Amqp.waitForConnection(actorSystem, child).await()
  }


  private def getCorrelationId(delivery: Amqp.Delivery) = {
    val id = delivery.properties.getHeaders.get(Headers.CorrelationId)
    if (id == null) null else id.toString
  }

  private def logs(prefix: String, routingKey: String, body: Array[Byte], correlationId: String, e: Throwable = null) {
    if (log.underlying.isTraceEnabled) {
      if (correlationId != null) {
        MDC.put("correlation_id", correlationId)
      }

      val msg = s"sbus $prefix $routingKey: ${new String(body.take(512))}"
      if (e == null) log.trace(msg) else log.error(msg, e)
    }
  }
}
