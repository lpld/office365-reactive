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
    ws = StandaloneAhcWSClient(),
    credential = CredentialData(
      initialToken = Some(TokenSuccess(
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6ImlCakwxUmNxemhpeTRmcHhJeGRacW9oTTJZayIsImtpZCI6ImlCakwxUmNxemhpeTRmcHhJeGRacW9oTTJZayJ9.eyJhdWQiOiJodHRwczovL291dGxvb2sub2ZmaWNlMzY1LmNvbS8iLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC84NWQyYTViYi0xZDlmLTQxMDUtYTRjNi0zMjczOTAyMTc5NmIvIiwiaWF0IjoxNTI2NjQxMzczLCJuYmYiOjE1MjY2NDEzNzMsImV4cCI6MTUyNjY0NTI3MywiYWNjdCI6MCwiYWNyIjoiMSIsImFpbyI6IlkyZGdZR0F1S3RpdTFaUWlkL0RQM2plcUMvNnQ1TXc3M25YNnlnbStXam1qYlpOOGxLVUEiLCJhbXIiOlsicHdkIl0sImFwcF9kaXNwbGF5bmFtZSI6IlRocmVhZEJvdCIsImFwcGlkIjoiMjg3NGIzYzEtMGUzOS00ZjM0LWEwMjktOWU3MTgxNTQ2OGVlIiwiYXBwaWRhY3IiOiIxIiwiZV9leHAiOjI2MjgwMCwiZW5mcG9saWRzIjpbXSwiZmFtaWx5X25hbWUiOiJTaXZhc2hvdiIsImdpdmVuX25hbWUiOiJMZXYiLCJpcGFkZHIiOiI0Ni45OC4yNDguNjQiLCJuYW1lIjoiTGV2IFNpdmFzaG92Iiwib2lkIjoiMzRjMzY2OTAtYmVjMi00MmI4LTgxOTItYmQyNDZhNDYyMjRmIiwicHVpZCI6IjEwMDM3RkZFOUNCQUY0RDkiLCJwd2RfZXhwIjoiMTE1MTQzMSIsInB3ZF91cmwiOiJodHRwczovL3BvcnRhbC5taWNyb3NvZnRvbmxpbmUuY29tL0NoYW5nZVBhc3N3b3JkLmFzcHgiLCJzY3AiOiJDYWxlbmRhcnMuUmVhZFdyaXRlIGZ1bGxfYWNjZXNzX2FzX3VzZXIgTWFpbC5SZWFkV3JpdGUgTWFpbC5TZW5kIiwic3ViIjoiVWFwWTI5UmZhQlR6VU1UN216d19EdUx0X3ZJSlNaejU3NV9wbnZfcjhlTSIsInRpZCI6Ijg1ZDJhNWJiLTFkOWYtNDEwNS1hNGM2LTMyNzM5MDIxNzk2YiIsInVuaXF1ZV9uYW1lIjoibGV2QHlveGVsLm5ldCIsInVwbiI6ImxldkB5b3hlbC5uZXQiLCJ1dGkiOiJkZ0x0RXJmRXhVLUpRNWxscVVzZUFBIiwidmVyIjoiMS4wIn0.Na10WsTkJ5a1I5Jtaqca8aARdItIUe1fJL3oigwSAhFfQPnqcpOPaDrHIkSQ26CCOKUmZf1DPWXrbet0q8HnBC72vVJCxWsYSVMrCs5OtH4PdeqQRth3AVdFgKGxxISpCjSuXOVJ9_xMrIdJ0VrCF8tRDdfK4HqJJ3QUUH6Kbh-D2IYeGKkScaETf9CBpAnaKrJrd0_6bJwmA-8MDck-HlP4p3ilD_nvLCr9nz6IcgvPdsq34J-ad9BWgyKIKlsu4OR68AIFcSFvpE0wgYywghBICHyTgaxPNG_3M6lcMU-YH9oz_VoI1PNqU47_ft0LpSDXFnJpZYiBnOLN2oterg",
        System.currentTimeMillis() + 10.minutes.toMillis
      )),
      refreshAction = () => Future.successful(TokenFailure(critical = true, "Cannot refresh"))
    ),
    defaultPageSize = 5
  )

  loadMessages()
  //  loadMessagesWithExtendedProperty()
  //  loadMessageById()
  //  loadExtendedMessageById()
  //  loadMessagesFromFolder()

  def loadMessages(): Unit = {

    val messages = api.query[MessageFull](
      filter = s"ReceivedDateTime ge ${Instant.now() minus Duration.ofDays(100)}",
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

