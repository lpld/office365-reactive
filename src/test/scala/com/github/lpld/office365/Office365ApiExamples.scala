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
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6ImlCakwxUmNxemhpeTRmcHhJeGRacW9oTTJZayIsImtpZCI6ImlCakwxUmNxemhpeTRmcHhJeGRacW9oTTJZayJ9.eyJhdWQiOiJodHRwczovL291dGxvb2sub2ZmaWNlMzY1LmNvbS8iLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC84NWQyYTViYi0xZDlmLTQxMDUtYTRjNi0zMjczOTAyMTc5NmIvIiwiaWF0IjoxNTI2ODQ5MzY0LCJuYmYiOjE1MjY4NDkzNjQsImV4cCI6MTUyNjg1MzI2NCwiYWNjdCI6MCwiYWNyIjoiMSIsImFpbyI6IkFTUUEyLzhIQUFBQVZhek9USHdXZmdENjNzSUsyS0lycEJ5eWtoeWNkZjFJV0pzRUY2dG1VZGM9IiwiYW1yIjpbInB3ZCJdLCJhcHBfZGlzcGxheW5hbWUiOiJUaHJlYWRCb3QiLCJhcHBpZCI6IjI4NzRiM2MxLTBlMzktNGYzNC1hMDI5LTllNzE4MTU0NjhlZSIsImFwcGlkYWNyIjoiMSIsImVfZXhwIjoyNjI4MDAsImVuZnBvbGlkcyI6W10sImZhbWlseV9uYW1lIjoiU2l2YXNob3YiLCJnaXZlbl9uYW1lIjoiTGV2IiwiaXBhZGRyIjoiNDYuOTguMjQ4LjY0IiwibmFtZSI6IkxldiBTaXZhc2hvdiIsIm9pZCI6IjM0YzM2NjkwLWJlYzItNDJiOC04MTkyLWJkMjQ2YTQ2MjI0ZiIsInB1aWQiOiIxMDAzN0ZGRTlDQkFGNEQ5IiwicHdkX2V4cCI6Ijk0MzQ0MCIsInB3ZF91cmwiOiJodHRwczovL3BvcnRhbC5taWNyb3NvZnRvbmxpbmUuY29tL0NoYW5nZVBhc3N3b3JkLmFzcHgiLCJzY3AiOiJDYWxlbmRhcnMuUmVhZFdyaXRlIGZ1bGxfYWNjZXNzX2FzX3VzZXIgTWFpbC5SZWFkV3JpdGUgTWFpbC5TZW5kIiwic3ViIjoiVWFwWTI5UmZhQlR6VU1UN216d19EdUx0X3ZJSlNaejU3NV9wbnZfcjhlTSIsInRpZCI6Ijg1ZDJhNWJiLTFkOWYtNDEwNS1hNGM2LTMyNzM5MDIxNzk2YiIsInVuaXF1ZV9uYW1lIjoibGV2QHlveGVsLm5ldCIsInVwbiI6ImxldkB5b3hlbC5uZXQiLCJ1dGkiOiJPRkE0cmMyclJrMldBLWk5c3ZnYUFBIiwidmVyIjoiMS4wIn0.DRjtf2Xf49qaWtfWkmJrarLGLMIfwgUBA36GdpYao9TNxCSmyHNaQxZ57pQVtHuco8wNG-r03aaeZGFU2MbMoND1QI4eDYiJgCq-5r8Cyo6lyTJlpjFLSFiXqW__JzCwZT1O24_MtfSR2R_pHK73pkUp7_r6RM8q6zflQ5DLRjhqOxo_azAckLUgjhP06uJxPHti5JgxMlBbkZyLtDYEXllybGcvSj5s3x9DfBEfITYUARjjpey3XXMKOC64I6nTyuVPM_8l4eQQQmg-ZrL3Cr104UnWp2a6cC3Jjz7zj1LTTs3eWQVfqL53B1oEnzObd9Do32anUm3FDQHV6HsSLA",
        System.currentTimeMillis() + 10.minutes.toMillis
      )),
      refreshAction = () => Future.successful(TokenFailure(critical = true, "Cannot refresh"))
    ),
    defaultPageSize = 5
  )

  loadMessages()
  loadMessagesWithExtendedProperty()
  loadMessageById()
  loadExtendedMessageById()
  loadMessagesFromFolder()

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

