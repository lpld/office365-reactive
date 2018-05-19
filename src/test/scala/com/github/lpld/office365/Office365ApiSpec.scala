package com.github.lpld.office365

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import com.github.lpld.office365.TokenRefresher.TokenSuccess
import com.github.lpld.office365.model.OMessage
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import play.api.libs.json.{Json, Reads}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationLong

/**
  * @author leopold
  * @since 19/05/18
  */
class Office365ApiSpec
  extends TestKit(ActorSystem("office365-api"))
    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with MockFactory {


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
    client = http,
    baseUrl = "http://test.com",
    credential = CredentialData(
      Some(TokenSuccess("xxx", expiresAt = System.currentTimeMillis() + 20.minutes.toMillis)),
      () => Future(fail("Should not be called"))(ExecutionContext.global)
    ),
    defaultPageSize = 5
  )

  "Office365 api" should {
    "generate $select query based on item's schema" in {

      expectHttpRequest("GET", "/messages/abc",
        "$select" -> "Id,Subject,ReceivedDateTime"
      ).returning(Future.successful(MsgItem("abc", "yyy", Instant.now())))

      Await.result(
        api.get[MsgItem]("abc").runWith(Sink.head),
        1.second
      ).Subject shouldEqual "yyy"
    }
  }

  private def expectHttpRequest[T](method: String, path: String, params: (String, String)*) =
    (http.request[T](_: String, _: String, _: Seq[(String, String)], _: Seq[(String, String)])(_: Reads[T]))
      .expects(where { (url, httpMethod, headers, queryParams, _) =>
        url == "http://test.com" + path &&
          httpMethod == method &&
          headers.find(_._1 == "Authorization").exists(_._2 == "Bearer xxx") &&
          Map(params: _*) == Map(queryParams: _*)
      })


}

