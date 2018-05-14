package com.github.lpld.office365

import java.io.IOException

import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source, UnzipWith}
import com.github.lpld.office365.Office365Api.{Req, Resp, manyReads}
import com.github.lpld.office365.http.WSClientAdapter
import play.api.libs.json._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.{StandaloneWSRequest, StandaloneWSResponse}

/**
  * @author leopold
  * @since 14/05/18
  */
class Office365Api
(
  ws: WSClientAdapter,
  credential: Credential,

  // todo: move this options to config
  preferredBodyType: BodyType = BodyType.Html,
  defaultPageSize: Int = 100
) {

  private val helper = new Helper(ws, credential.accessToken, preferredBodyType, defaultPageSize)

  /**
    * Get entity by ID
    */
  def get[E: Entity : Reads](id: String): Source[E, _] =
    helper.prepareRequest(s"${helper.getPath[E]}/$id")
      .via(helper.execute("GET"))
      .via(helper.handle404)
      .via(helper.parse[E])

  def query[E: Entity : Reads]($select: String = null,
                               $filter: String = null,
                               $orderby: String = null): Source[E, _] = {

    val params = List(
      "$select" -> $select,
      "$filter" -> $filter,
      "$orderby" -> $orderby
    ).filterNot(_._1 == null)

    helper.getPaged[E](params: _*)
  }
}

object Office365Api {
  val baseUrl = "https://outlook.office.com/api/v2.0/me"

  def extractPath(fullUrl: String): String = {
    require(fullUrl startsWith baseUrl)
    fullUrl substring baseUrl.length
  }

  type Req = StandaloneWSRequest
  type Resp = StandaloneWSResponse

  implicit def manyReads[T: Reads]: Reads[Many[T]] = Reads { json =>
    JsSuccess(Many(
      value = (json \ "value").as[List[T]],
      nextUrl = Option((json \ "@odata.nextLink").as[String])
    ))
  }
}

// todo: handle refresh token
private class Helper(ws: WSClientAdapter, accessToken: String, bodyType: BodyType, pageSize: Int) {

  /**
    * Create an http request with common parameters (authentication, http headers).
    */
  def prepareRequest(path: String): Source[Req, _] = Source.single {
    ws.url(s"${Office365Api.baseUrl}$path")
      .withHttpHeaders(
        "Authorization" -> s"Bearer $accessToken",
        "Accept" -> "application/json",
        "Prefer" -> s"""outlook.body-content-type="${bodyType.name}""""
      )
  }

  /**
    * Execute request and wrap bad status in Office365ResponseException.
    */
  def execute(method: String): Flow[Req, Resp, _] =
    Flow[Req]
      .mapAsync(1) { req =>
        //        logger.info(s"Making request ${req.url}")
        req.withMethod(method).execute()
      }
      .collect {
        case resp if resp.status == 200 =>
          resp
        case failed =>
          //          logger.error("Http error", failed.body)
          throw Office365ResponseException(failed.status, failed.statusText, failed.body)
      }

  /**
    * Handle 404: produce empty stream in case of 404 error.
    */
  def handle404[T]: Flow[T, T, _] =
    Flow[T]
      .map(List(_))
      .recover {
        case Office365ResponseException(404, _, _) => Nil
      }
      .mapConcat(identity)

  /**
    * Parse response into a JSON value
    */
  def parse[T: Reads]: Flow[Resp, T, _] =
    Flow[Resp].map(_.body[JsValue].as[T])

  def getPath[E: Entity]: String = implicitly[Entity[E]].apiPath

  /**
    * Add query parameters to the request
    */
  private def withQueryParams(params: Seq[(String, String)]): Flow[Req, Req, _] =
    Flow[Req].map(_.withQueryStringParameters(params: _*))

  private def getMany[T: Reads](path: String, params: (String, String)*): Source[Many[T], _] =
    prepareRequest(path)
      .via(withQueryParams(params))
      .via(execute("GET"))
      .via(parse[Many[T]])

  private def getFirstPage[T: Entity : Reads](query: (String, String)*): Source[Many[T], _] =
    getMany[T](
      path = getPath[T],
      params = query ++ List("$top" -> s"$pageSize", "$skip" -> "0"): _*
    )

  /**
    * Returns a source that contains data from paged query to the API.
    */
  def getPaged[T: Entity : Reads](params: (String, String)*): Source[T, _] = {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // loading first page from the API
      val firstPage = getFirstPage[T](params: _*)

      // merging results from firstPage and feedback loop
      val merge = b.add(Merge[Many[T]](2))

      // unzipping Many into List[T] and Option[String] representing next records url.
      val unzip = b.add(UnzipWith((many: Many[T]) => (many.value, many.nextUrl)))

      // in order for the cycle to complete we need to add takeWhile here. otherwise merge stage
      // will never be completed.
      val nextPagePath = Flow[Option[String]]
        .takeWhile(_.isDefined)
        .mapConcat(_.toList)
        .map(Office365Api.extractPath)

      // loading next page from the API
      val loadPage = Flow[String].flatMapConcat(path => getMany[T](path))

      val flatten = b.add(Flow[List[T]].mapConcat(identity))

      // the graph itself:
      // @formatter:off
      firstPage ~> merge             ~>                 unzip.in
                   merge <~ loadPage <~ nextPagePath <~ unzip.out1
                                                        unzip.out0 ~> flatten
      // @formatter:on

      SourceShape(flatten.out)
    })
  }
}

class Office365Exception(message: String) extends IOException(message)

case class Office365ResponseException(status: Int, statusText: String, errorDetails: String)
  extends Office365Exception(s"$status - $statusText: $errorDetails")

case class Many[T](value: List[T], nextUrl: Option[String])

//                   `@odata.context`: String,
//                   `@odata.nextLink`: Option[String])

sealed abstract class BodyType(val name: String)

object BodyType {

  case object Text extends BodyType("text")

  case object Html extends BodyType("html")

}