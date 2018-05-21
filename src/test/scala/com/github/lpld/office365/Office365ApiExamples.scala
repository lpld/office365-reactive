package com.github.lpld.office365

import java.time.{Duration, Instant}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.github.lpld.office365.TokenRefresher.{TokenFailure, TokenSuccess}
import com.github.lpld.office365.model._
import play.api.libs.json.Json
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future}

/**
  * @author leopold
  * @since 15/05/18
  */
object Office365ApiExamples extends App {

  implicit val system = ActorSystem("api-examples")
  implicit val materializer = ActorMaterializer()

  val api = Office365Api(
    httpClient = new PlayWsHttpClient(StandaloneAhcWSClient()),
    credential = CredentialData(
      initialToken = Some(TokenSuccess(
        System.getProperty("office.token"),
        System.currentTimeMillis() + 10.minutes.toMillis
      )),
      refreshAction = () => Future.successful(TokenFailure(critical = true, "Cannot refresh"))
    ),
    defaultPageSize = 5
  )

  sendMessage()
  //  loadMessages()
  //  loadMessagesWithExtendedProperty()
  //  loadMessageById()
  //  loadExtendedMessageById()
  //  loadMessagesFromFolder()
  api.close()
  Await.result(system.terminate(), 10.seconds)

  case class NewMessage(Subject: String,
                        ToRecipients: List[Recipient],
                        Body: ItemBody) extends OMessage

  object NewMessage {
    implicit val writes = Json.writes[NewMessage]
  }

  def sendMessage(): Unit = {
    val send = api.sendmail(NewMessage(
      Subject = "Testing api",
      ToRecipients = List(Recipient(EmailAddress(Address = Some("lsivashov@gmail.com"), Name = None))),
      Body = ItemBody(Content = "test123", ContentType = "Text")
    ))

    Await.result(send.runWith(Sink.head), 5.seconds)
    println("done")
  }

  def loadMessages(): Unit = {

    val messages = api.query[MessageFull](
      filter = s"ReceivedDateTime ge ${Instant.now() minus Duration.ofDays(300)}",
      orderby = "ReceivedDateTime"
    )

    Await.result(messages.runForeach(println), 30.seconds)
  }

  def loadMessagesFromFolder(): Unit = {
    Await.result(
      api
        .from(WellKnownFolder.SentItems)
        .queryAll[MessageFull]
        .runForeach(println),

      30.seconds
    )
  }

  def loadEventsFromFolder(): Unit = {
    Await.result(
      api
        .from(FolderType.Calendar, "asdfasdf")
        .queryAll[EventIdOnly]
        .runForeach(println),

      30.seconds
    )
  }

  // Item with extended properties.
  case class MessageExtended
  (
    Id: String,
    Subject: String,
    SingleValueExtendedProperties: List[SingleValueProperty]
  ) extends OMessage with ExtendedPropertiesSupport {

    def itemClass: Option[String] = getProp(ExtendedProperties.ItemClassProp)

    def inReplyTo: Option[String] = getProp(ExtendedProperties.InReplyTo)
  }

  // Companion object for item with implicit json Reads and Schema
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
    val message = api.get[MessageFull](id).runWith(Sink.head)

    println(Await.result(message, 30.seconds))
  }

  def loadExtendedMessageById(): Unit = {

    val id = "AQMkAGY0ZTQyM2ZlLTM5N2UtNGZkYy1hZmQ2LTJkNDdmNTlmMGZlNgBGAAADhXGu-WVhvEKOWPBUZKnvPwcAAAAUsezTfrRCmmimIKi3ETUAAAIBDAAAARSx7NN_tEKaaKYgqLcRNQABSq7DNAAAAA=="
    val message = api.get[MessageExtended](id).runWith(Sink.head)

    println(Await.result(message, 30.seconds))
  }


}

object ExtendedProperties {

  val ItemClassProp = ExtendedProperty("ItemClass", "String 0x1a")
  val InReplyTo = ExtendedProperty("InReplyTo", "String 0x1042")
}

