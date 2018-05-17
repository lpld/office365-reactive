package com.github.lpld.office365

import java.io.IOException

import akka.NotUsed
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source, UnzipWith}
import com.github.lpld.office365.Office365Api.{Req, Resp}
import play.api.libs.json._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}

/**
  * High-level API client, that takes care of API paths, property sets for entities and pagination.
  *
  * @author leopold
  * @since 14/05/18
  */
class Office365Api
(
  ws: StandaloneWSClient,
  credential: CredentialData,

  // todo: move this options to config
  preferredBodyType: BodyType = BodyType.Html,
  defaultPageSize: Int = 100
) {

  private val client = new Office365Client(ws, credential, preferredBodyType)

  /**
    * Get entity by ID.
    */
  def get[E: Schema : Reads : Path](id: String): Source[E, NotUsed] =
    client.getOne(
      path = s"${pathFor[E]}/$id",
      queryParams = queryParamsFor[E]: _*
    )

  /**
    * Query multiple entities.
    */
  def query[E: Schema : Reads : Path](filter: String = null,
                                      orderby: String = null): Source[E, NotUsed] =
    getPaged[E](queryParamsFor[E](filter, orderby): _*)


  /**
    * Returns a source that contains data from paged query to the API.
    */
  def getPaged[E: Path : Reads](params: (String, String)*): Source[E, NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // loading first page from the API
      val firstPage = client.getMany[E](pathFor[E], params ++ List("$top" -> s"$defaultPageSize", "$skip" -> "0"): _*)

      // merging results from firstPage and feedback loop
      val merge = b.add(Merge[Many[E]](2))

      // unzipping Many into List[E] and Option[String] representing next records url.
      val unzip = b.add(UnzipWith((many: Many[E]) => (many.value, many.`@odata.nextLink`)))

      // in order for the cycle to complete we need to add takeWhile here. otherwise merge stage
      // will never be completed.
      val nextPagePath = Flow[Option[String]]
        .takeWhile(_.isDefined)
        .mapConcat(_.toList)
        .map(Office365Api.extractPath)

      // loading next page from the API
      val loadPage = Flow[String].flatMapConcat(path => client.getMany[E](path))

      val flatten = b.add(Flow[List[E]].mapConcat(identity))

      // the graph itself:
      // @formatter:off
      firstPage ~> merge             ~>                 unzip.in
                   merge <~ loadPage <~ nextPagePath <~ unzip.out1
                                                        unzip.out0 ~> flatten
      // @formatter:on

      SourceShape(flatten.out)
    })
  }

  def close(): Unit = client.close()

  private def pathFor[E: Path]: String = implicitly[Path[E]].apiPath

  private def schemaFor[E: Schema]: Schema[E] = implicitly[Schema[E]]

  private def queryParamsFor[E: Schema]: List[(String, String)] = queryParamsFor[E](null, null)

  private def queryParamsFor[E: Schema](filter: String, orderby: String): List[(String, String)] = {
    val schema = schemaFor[E]

    def expand: String =
      if (schema.extendedProperties.isEmpty) null
      else {
        val propsClause = schema.extendedProperties
          .map(p => s"PropertyId eq '${p.propertyId}'")
          .mkString(" OR ")
        s"SingleValueExtendedProperties($$filter=$propsClause)"
      }

    List(
      "$select" -> schema.standardProperties.mkString(","),
      "$filter" -> filter,
      "$orderby" -> orderby,
      "$expand" -> expand
    ).filterNot(_._2 == null)
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

}

object Office365Client {
  implicit def manyReads[E: Reads]: Reads[Many[E]] = Json.reads[Many[E]]
}

/**
  * Low-level API client
  */
class Office365Client(ws: StandaloneWSClient, credentialData: CredentialData, preferredBodyType: BodyType) {

  val tokenSource = TokenSource(credentialData)

  import Office365Client.manyReads

  def getOne[E: Reads](path: String, queryParams: (String, String)*): Source[E, NotUsed] =
    prepareRequest(path)
      .via(withQueryParams(queryParams))
      .via(execute("GET"))
      .via(handle404)
      .via(parse[E])

  def getMany[T: Reads](path: String, params: (String, String)*): Source[Many[T], NotUsed] =
    prepareRequest(path)
      .via(withQueryParams(params))
      .via(execute("GET"))
      .via(parse[Many[T]])

  /**
    * Create an http request with common parameters (authentication, http headers).
    */
  private def prepareRequest(path: String): Source[Req, NotUsed] =
    tokenSource.credential
      .map(accessToken =>
        ws.url(s"${Office365Api.baseUrl}$path")
          .withHttpHeaders(
            "Authorization" -> s"Bearer $accessToken",
            "Accept" -> "application/json",
            "Prefer" -> s"""outlook.body-content-type="${preferredBodyType.name}""""
          )
      )

  /**
    * Execute request and wrap bad status in Office365ResponseException.
    */
  private def execute(method: String): Flow[Req, Resp, NotUsed] =
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
  private def handle404[T]: Flow[T, T, NotUsed] =
    Flow[T]
      .map(List(_))
      .recover {
        case Office365ResponseException(404, _, _) => Nil
      }
      .mapConcat(identity)

  /**
    * Parse response into a JSON value
    */
  private def parse[T: Reads]: Flow[Resp, T, NotUsed] =
    Flow[Resp].map(_.body[JsValue].as[T])

  /**
    * Add query parameters to the request
    */
  private def withQueryParams(params: Seq[(String, String)]): Flow[Req, Req, NotUsed] =
    Flow[Req].map(_.withQueryStringParameters(params: _*))

  def close(): Unit = tokenSource.close()
}

class Office365Exception(message: String) extends IOException(message)

case class Office365ResponseException(status: Int, statusText: String, errorDetails: String)
  extends Office365Exception(s"$status - $statusText: $errorDetails")

case class Many[T](value: List[T],
                   `@odata.context`: String,
                   `@odata.nextLink`: Option[String])

sealed abstract class BodyType(val name: String)

object BodyType {

  case object Text extends BodyType("text")

  case object Html extends BodyType("html")

}