package co.uk.vturbo.sbus.rabbitmq

import java.util
import java.util.UUID
import java.util.concurrent.{CompletionException, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.pattern.{ask, AskTimeoutException}
import akka.util.Timeout
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.github.sstone.amqp._
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.{LoggerFactory, MDC}

import co.uk.vturbo.sbus.model._


class RabbitMqTransport(conf: Config, actorSystem: ActorSystem, mapper: ObjectMapper) extends Transport {

  implicit val ec = actorSystem.dispatcher

  private val log = Logger(LoggerFactory.getLogger("sbus.rabbitmq"))

  implicit val defaultTimeout = Timeout(conf.getDuration("default-timeout").toMillis, TimeUnit.MILLISECONDS)

  private val DefaultCommandRetries = conf.getInt("default-command-retries")
  private val ChannelParams         = Amqp.ChannelParameters(qos = conf.getInt("prefetch-count"), global = false)
  private val CommonExchange        = Amqp.ExchangeParameters(conf.getString("exchange"), passive = false, exchangeType = "direct")
  private val RetryExchange         = Amqp.ExchangeParameters(conf.getString("retry-exchange"), passive = false, exchangeType = "fanout")


  private val connection = actorSystem.actorOf(ConnectionOwner.props({
    log.debug("Sbus connecting to: " + conf.getString("host"))

    val cf = new ConnectionFactory()
    cf.setHost(conf.getString("host"))
    cf.setPort(conf.getInt("port"))
    cf
  }, 3.seconds), name = "rabbitmq-connection")

  private val producer = {
    val channelOwner = ConnectionOwner.createChildActor(connection, ChannelOwner.props())
    Amqp.waitForConnection(actorSystem, channelOwner).await()

    for {
      _ ← channelOwner ? Amqp.DeclareExchange(CommonExchange)
      _ ← channelOwner ? Amqp.DeclareExchange(RetryExchange)

      _ ← channelOwner ? Amqp.DeclareQueue(
        Amqp.QueueParameters("retries", passive = false, durable = true, exclusive = false, autodelete = false, args = Map("x-dead-letter-exchange" → CommonExchange.name)))

      _ ← channelOwner ? Amqp.QueueBind("retries", RetryExchange.name, "#")
    } {}

    channelOwner
  }

  private val rpcClient = {
    val child = ConnectionOwner.createChildActor(connection, RpcClient.props(Some(ChannelParams)))
    Amqp.waitForConnection(actorSystem, child).await()
    child
  }


  /**
   *
   */
  def send(routingKey: String, msg: Any, context: Context, responseClass: Class[_]): Future[Any] = {
    val bytes = mapper.writeValueAsBytes(new Message(routingKey, msg))

    val corrId = Option(context.correlationId).getOrElse(UUID.randomUUID().toString)

    val propsBldr = new BasicProperties().builder()
      .deliveryMode(if (responseClass != null) 1 else 2)   // 2 → persistent
      .messageId(context.get(Headers.ClientMessageId).getOrElse(UUID.randomUUID()).toString)
      .headers(Map(
        Headers.CorrelationId    → corrId,
        Headers.RetryAttemptsMax → context.maxRetries.getOrElse(if (responseClass != null) 0 else DefaultCommandRetries) // commands retriable by default
      ).mapValues(_.toString.asInstanceOf[Object]).asJava)

    // timeout only for req/resp messages
    if (responseClass != null && context.timeout.nonEmpty) {
      propsBldr.expiration(context.timeout.get.toString)
    }

    logs("~~~>", routingKey, bytes, corrId)

    val pub = Amqp.Publish(CommonExchange.name, routingKey, bytes, Some(propsBldr.build()))

    (if (responseClass != null) {
      meter("request", routingKey) {
        rpcClient.ask(RpcClient.Request(pub))(context.timeout.fold(defaultTimeout)(_.millis)) map {
          case RpcClient.Response(deliveries) ⇒
            logs("resp <~~~", routingKey, deliveries.head.body, corrId)

            val tree = mapper.readTree(deliveries.head.body)

            val status =
              if (tree.hasNonNull("status")) {
                tree.path("status").asInt
              } else if (tree.path("failed").asBoolean(false)) { // backward compatibility with old protocol
                500
              } else { 200 }

            if (status < 400)  {
              deserializeToClass(tree.path("body"), responseClass)
            } else {
              val err = mapper.treeToValue(tree.path("body"), classOf[ErrorResponseBody])
              throw new ErrorMessage(status, err.getMessage, error = err.getError, _links = err.getLinks)
            }

          case other ⇒
            throw new ErrorMessage(500, "Unexpected request result " + routingKey + ":" + other)
        }
      }
    } else {
      producer ? pub map {
        case _: Amqp.Ok ⇒ // ok
        case error ⇒ throw new ErrorMessage(500, "Error on publish message to " + routingKey + ": " + error)
      }
    }) recover {
      case e: AskTimeoutException ⇒
        logs("timeout error", routingKey, bytes, corrId, e)
        throw new ErrorMessage(504, s"Timeout on `$routingKey` with message: $msg", e)

      case e: Throwable ⇒
        logs("error", routingKey, bytes, corrId, e)
        throw e
    }
  }

  /**
   *
   */
  def subscribe[T](routingKey: String, messageClass: Class[_], handler: (T, Context) ⇒ Future[Any]): Unit = {
    require(messageClass != null, "messageClass is required!")

    val processor = new RpcServer.IProcessor {

      def process(delivery: Amqp.Delivery): Future[RpcServer.ProcessResult] = {
        meter("handle", routingKey) {
          (try {
            logs("<~~~", routingKey, delivery.body, getCorrelationId(delivery))

            val payload = (mapper.readTree(delivery.body).get("body") match {
              case null ⇒ null
              case body ⇒ deserializeToClass(body, messageClass)
            }).asInstanceOf[T]

            (try handler(payload, Context.from(delivery)) catch {
              case e: Throwable ⇒ Future.failed(e)
            }) map {
              case result if delivery.properties.getReplyTo != null ⇒
                val bytes = mapper.writeValueAsBytes(new Response(200, result))
                logs("resp ~~~>", routingKey, bytes, getCorrelationId(delivery))
                RpcServer.ProcessResult(Some(bytes))

              case _ ⇒ RpcServer.ProcessResult(None)
            } recover {
              case e: CompletionException if e.getCause != null ⇒ throw e.getCause // unwrap java future errors
            } recoverWith {
              case e @ (_: NullPointerException | _: IllegalArgumentException | _: JsonProcessingException) ⇒
                throw new BadRequestError(e.getMessage, e)

              case e: Throwable if !e.isInstanceOf[UnrecoverableFailure]  ⇒
                val heads       = Option(delivery.properties.getHeaders).getOrElse(new util.HashMap[String, Object]())
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
            case e: CompletionException ⇒ onFailure(delivery, e.getCause)
            case NonFatal(e: Exception) ⇒ onFailure(delivery, e)
          }
        }
      }

      def onFailure(delivery: Amqp.Delivery, e: Throwable): RpcServer.ProcessResult = {
        logs("error", routingKey, e.toString.getBytes, getCorrelationId(delivery), e)

        if (delivery.properties.getReplyTo != null) {
          val response = e match {
            case em: ErrorMessage ⇒ new Response(em.code, new ErrorResponseBody(em.getMessage, em.error, em._links))
            case _                ⇒ new Response(500, new ErrorResponseBody(e.getMessage, null, null))
          }

          val bytes = mapper.writeValueAsBytes(response)
          logs("resp ~~~>", routingKey, bytes, getCorrelationId(delivery))
          RpcServer.ProcessResult(Some(bytes))
        } else {
          RpcServer.ProcessResult(None)
        }
      }
    }

    val rpcServer = ConnectionOwner.createChildActor(connection, RpcServer.props(
      queue         = Amqp.QueueParameters(routingKey, passive = false, durable = false, exclusive = false, autodelete = false),
      exchange      = CommonExchange,
      routingKey    = routingKey,
      proc          = processor,
      channelParams = ChannelParams
    ))

    log.debug("Sbus subscribed to: " + routingKey)

    Amqp.waitForConnection(actorSystem, rpcServer).await()
  }

  private def deserializeToClass(node: JsonNode, responseClass: Class[_]): Any = {
    if (responseClass == classOf[java.lang.Void] || responseClass == java.lang.Void.TYPE || responseClass.isInstance(Unit)) {
      // return nothing
    } else {
      mapper.treeToValue(node, responseClass)
    }
  }

  private def getCorrelationId(delivery: Amqp.Delivery): String = {
    val heads = delivery.properties.getHeaders

    if (heads != null) {
      val id = heads.get(Headers.CorrelationId)
      if (id == null) null else id.toString
    } else null
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
