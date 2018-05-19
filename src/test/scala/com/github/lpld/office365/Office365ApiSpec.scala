package com.github.lpld.office365

import java.time.Instant

import akka.stream.scaladsl.Source
import com.github.lpld.office365.model.OMessage
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import play.api.libs.json.{Json, Reads}

/**
  * @author leopold
  * @since 19/05/18
  */
class Office365ApiSpec
  extends WordSpec
    with BeforeAndAfter
    with Matchers
    with MockFactory {

  case class MsgItem(Id: String, Subject: String, ReceivedDateTime: Instant) extends OMessage

  object MsgItem {
    implicit val reads = Json.reads[MsgItem]
    implicit val schema = Schema[MsgItem]
  }

  val lowLevelClient: LowLevelClient = mock[LowLevelClient]
  val api = new Office365Api(lowLevelClient, defaultPageSize = 5)

  "Office365 api" should {
    "generate $select query based on item's schema" in {

      (lowLevelClient.getOne[MsgItem](_: String, _: Seq[(String, String)])(_: Reads[MsgItem]))
        .expects(where { (path, params, _) =>
          val queryParams = Map(params: _*)
          path == "/messages/xxx" && queryParams("$select") == "Id,Subject,ReceivedDateTime"
        })
        .returning(Source.single(MsgItem("xxx", "yyy", Instant.now())))

      api.get[MsgItem]("xxx")
    }
  }

}

