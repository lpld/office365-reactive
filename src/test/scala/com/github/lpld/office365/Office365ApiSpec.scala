package com.github.lpld.office365

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import akka.stream.{ActorMaterializer, Attributes, Inlet, SinkShape}
import akka.testkit.TestKit
import com.github.lpld.office365.TokenRefresher.TokenSuccess
import com.github.lpld.office365.model.{OMessage, WellKnownFolder}
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import play.api.libs.json.{Json, Reads}

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * @author leopold
  * @since 19/05/18
  */
class Office365ApiSpec
  extends TestKit(ActorSystem("office365-api"))
    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with MockFactory
    with LoneElement {


  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val materializer = ActorMaterializer()

  case class MsgItem(Id: String, Subject: String, ReceivedDateTime: Instant) extends OMessage

  object MsgItem {
    implicit val reads = Json.reads[MsgItem]
    implicit val schema = Schema[MsgItem]
  }

  val http = mock[HttpClient]
  val api = new Office365Api(
    httpClient = http,
    baseUrl = "http://test.com",
    credential = CredentialData(
      Some(TokenSuccess("xxx", expiresAt = System.currentTimeMillis() + 20.minutes.toMillis)),
      () => Future(fail("Should not be called"))(ExecutionContext.global)
    ),
    defaultPageSize = 5
  )

  "Office365 client" when {

    "executing single-entity queries" should {

      "generate $select query based on item's schema" in {

        expectHttpRequest(
          "GET", "/messages/abc",
          _ == Map("$select" -> "Id,Subject,ReceivedDateTime")
        ).returning(Future.successful(MsgItem("abc", "yyy", Instant.now())))


        run(api.get[MsgItem]("abc"))
          .loneElement
          .Subject shouldEqual "yyy"
      }

      "perform http request only when the source is materialized" in {
        val msg = api.get[MsgItem]("abc")

        expectHttpRequest[MsgItem]("GET", "/messages/abc")
          .returning(Future.successful(MsgItem("abc", "yyy", Instant.now())))

        run(msg)
      }
    }

    "executing multi-entity queries" should {

      val now = Instant.now()
      val pagedResult = api.query[MsgItem](
        filter = s"ReceivedDateTime ge $now",
        orderby = "ReceivedDateTime"
      )

      val firstPageItems = List(
        MsgItem("msg1", "msg1", Instant.now()),
        MsgItem("msg2", "msg2", Instant.now())
      )
      val secondPageItems = List(
        MsgItem("msg1", "msg1", Instant.now()),
        MsgItem("msg2", "msg2", Instant.now())
      )

      def expectPageCall(
                          path: String,
                          params: Map[String, String],
                          returningItems: List[MsgItem],
                          nextUrl: Option[String]
                        ) =
        expectHttpRequest[Many[MsgItem]]("GET", path, _ == params)
          .returning(Future.successful(Many(
            value = returningItems,
            `@odata.context` = "bla-bla",
            `@odata.nextLink` = nextUrl
          )))


      def expectFirstPage() =
        expectPageCall(
          "/messages",
          Map(
            "$select" -> "Id,Subject,ReceivedDateTime",
            "$filter" -> s"ReceivedDateTime ge $now",
            "$orderby" -> "ReceivedDateTime",
            "$top" -> "5",
            "$skip" -> "0"
          ),
          firstPageItems,
          Some("http://test.com/messages/next")
        )

      "load only pages that are being read from source" in {
        expectFirstPage()
        // loading only 2 entities from the first page:
        run(pagedResult.take(2)) shouldEqual firstPageItems
      }

      "load additional pages when needed" in {
        expectFirstPage()
        expectPageCall("/messages/next", Map(), secondPageItems, None)

        run(pagedResult.take(3)) shouldEqual firstPageItems :+ secondPageItems.head
      }

      "load items from specified folder" in {
        val result = api
          .from(WellKnownFolder.SentItems)
          .queryAll[MsgItem]

        expectPageCall(
          "/mailfolders/SentItems/messages",
          Map(
            "$select" -> "Id,Subject,ReceivedDateTime",
            "$top" -> "5",
            "$skip" -> "0"
          ),
          List(),
          None
        )

        run(result)
      }
    }
  }

  private def expectHttpRequest[T](method: String, path: String,
                                   paramsMatcher: Map[String, String] => Boolean = _ => true) =
    (http.request[T](_: String, _: String, _: Seq[(String, String)], _: Seq[(String, String)])(_: Reads[T]))
      .expects(where { (url, httpMethod, headers, queryParams, _) =>
        url == "http://test.com" + path &&
          httpMethod == method &&
          headers.find(_._1 == "Authorization").exists(_._2 == "Bearer xxx") &&
          paramsMatcher(Map(queryParams: _*))
      })


  private def run[I](source: Source[I, _]) = Await.result(source.runWith(Sink.seq), 100.millis)

}

class SinkReader[T] extends GraphStage[SinkShape[T]] {
  private val in: Inlet[T] = Inlet("sink-reader")

  override val shape: SinkShape[T] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = ???
      })
    }


}