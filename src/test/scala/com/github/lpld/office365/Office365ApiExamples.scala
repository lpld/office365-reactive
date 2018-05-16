package com.github.lpld.office365

import java.time.{Duration, Instant}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import play.api.libs.json.Json
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

/**
  * @author leopold
  * @since 15/05/18
  */
object Office365ApiExamples extends App {

  implicit val system = ActorSystem("api-examples")
  implicit val materializer = ActorMaterializer()

  val api = new Office365Api(
    ws = StandaloneAhcWSClient(),
    credential = Credential(
      "aaa",
      "xxx"
    ),
    defaultPageSize = 5
  )

  loadMessages()
  loadMessagesWithExtendedProperty()
  loadMessageById()
  loadExtendedMessageById()

  def loadMessages(): Unit = {

    val messages = api.query[Message](
      filter = s"ReceivedDateTime ge ${Instant.now() minus Duration.ofDays(100)}",
      orderby = "ReceivedDateTime"
    )

    Await.result(messages.runForeach(println), 30.seconds)
  }

  // Entity with extended properties.
  case class MessageExtended
  (
    Id: String,
    Subject: String,

    protected val SingleValueExtendedProperties: List[SingleValueProperty]
  ) extends OMessage with ExtendedPropertiesSupport {

    def itemClass: Option[String] = getProp(ExtendedProperties.ItemClassProp)

    def inReplyTo: Option[String] = getProp(ExtendedProperties.InReplyTo)
  }

  // Companion object for the entity with implicit json Reads and Schema
  object MessageExtended {
    implicit val reads = Json.reads[MessageExtended]

    // extended properties should be explicitly passed to the schema:
    implicit val schema = Schema[MessageExtended](ExtendedProperties.ItemClassProp, ExtendedProperties.InReplyTo)
  }

  def loadMessagesWithExtendedProperty(): Unit = {

    val messages = api.query[MessageExtended](
      filter = s"ReceivedDateTime ge ${Instant.now() minus Duration.ofDays(30)}"
    )

    Await.result(messages.runForeach(println), 30.seconds)
  }

  def loadMessageById(): Unit = {
    val id = "AQMkAGY0ZTQyM2ZlLTM5N2UtNGZkYy1hZmQ2LTJkNDdmNTlmMGZlNgBGAAADhXGu-WVhvEKOWPBUZKnvPwcAAAAUsezTfrRCmmimIKi3ETUAAAIBDAAAARSx7NN_tEKaaKYgqLcRNQABSq7DNAAAAA=="
    val message = api
      .get[Message](id)
      .toMat(Sink.head)(Keep.right)
      .run()

    println(Await.result(message, 30.seconds))
  }

  def loadExtendedMessageById(): Unit = {

    val id = "AQMkAGY0ZTQyM2ZlLTM5N2UtNGZkYy1hZmQ2LTJkNDdmNTlmMGZlNgBGAAADhXGu-WVhvEKOWPBUZKnvPwcAAAAUsezTfrRCmmimIKi3ETUAAAIBDAAAARSx7NN_tEKaaKYgqLcRNQABSq7DNAAAAA=="
    val message = api
      .get[MessageExtended](id)
      .toMat(Sink.head)(Keep.right)
      .run()

    println(Await.result(message, 30.seconds))
  }


}

object ExtendedProperties {

  val ItemClassProp = ExtendedProperty("ItemClass", "String 0x1a")
  val InReplyTo = ExtendedProperty("InReplyTo", "String 0x1042")
}

