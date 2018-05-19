package com.github.lpld.office365

import java.io.IOException

import akka.NotUsed
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source, UnzipWith}
import com.github.lpld.office365.Office365Api.{Req, Resp}
import com.github.lpld.office365.model._
import play.api.libs.json._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}

/**
  * A part of the API that contains some items in it. For example:
  *  - / (root)
  *  - /Mailfolders/Inbox
  *  - /Calendars/AAMkAGI2TG93AAA=
  * etc.
  *
  * It supports basic operations for manipulating items (for now, only retrieving)
  */
trait ItemBox[F <: Folder] {
  /**
    * Get an item by id.
    */
  def get[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](id: String): Source[I, NotUsed]

  /**
    * Query multiple items, optionally specifying filter and orderby parameters.
    */
  def query[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](filter: String = null, orderby: String = null): Source[I, NotUsed]

  /**
    * Query all items.
    */
  def queryAll[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api]: Source[I, NotUsed] = query(null, null)
}

object Office365Api {
  val defaultUrl = "https://outlook.office.com/api/v2.0/me"

  def extractPath(fullUrl: String): String = {
    require(fullUrl startsWith defaultUrl)
    fullUrl substring defaultUrl.length
  }

  type Req = StandaloneWSRequest
  type Resp = StandaloneWSResponse

  def apply(ws: StandaloneWSClient,
            credential: CredentialData,
            defaultPageSize: Int = 100,
            preferredBodyType: BodyType = BodyType.Html) =
    new Office365Api(
      new Office365Client(Office365Api.defaultUrl, ws, credential, preferredBodyType),
      defaultPageSize
    )
}

/**
  * High-level API client, that takes care of API paths, item schemas and pagination. Generally, should be
  * created using companion object.
  *
  * It extends {{{ItemBox[Folder]}}}, which basically means that it's a storage for items of all possible types.
  *
  * @author leopold
  * @since 14/05/18
  */
class Office365Api(client: LowLevelClient, defaultPageSize: Int = 100) extends ItemBox[Folder] {

  private def itemBox[F <: Folder](pathPrefix: String): ItemBox[F] =
    new ItemBoxImpl[F](client, pathPrefix, defaultPageSize)

  private val rootBox = itemBox[Folder]("")

  override def get[I <: Item with ChildOf[_ <: Folder] : Schema : Reads : Api](id: String): Source[I, NotUsed] =
    rootBox.get[I](id)

  override def query[I <: Item with ChildOf[_ <: Folder] : Schema : Reads : Api](filter: String, orderby: String): Source[I, NotUsed] =
    rootBox.query[I](filter, orderby)

  /**
    * Return ItemBox for a specific folder
    */
  def from[F <: Folder : Api](folderType: FolderType[F], id: String): ItemBox[F] =
    itemBox[F](s"${implicitly[Api[F]].path}/$id")

  /**
    * Return ItemBox for mail folder with well-known name
    */
  def from(wellKnownFolder: WellKnownFolder): ItemBox[OMailFolder] = from(FolderType.MailFolder, wellKnownFolder.name)

  def close(): Unit = client.close()
}

private class ItemBoxImpl[F <: Folder](client: LowLevelClient, pathPrefix: String, defaultPageSize: Int = 100) extends ItemBox[F] {

  override def get[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](id: String): Source[I, NotUsed] =
    client.getOne(
      path = s"${pathFor[I]}/$id",
      queryParams = queryParamsFor[I]
    )

  override def query[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](filter: String, orderby: String): Source[I, NotUsed] =
    getPaged[I](queryParamsFor[I](filter, orderby): _*)

  /**
    * Perform a paginated query to the API.
    */
  def getPaged[I: Api : Reads](params: (String, String)*): Source[I, NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // loading first page from the API
      val firstPage = client.getMany[I](pathFor[I], params ++ List("$top" -> s"$defaultPageSize", "$skip" -> "0"))

      // merging results from firstPage and feedback loop
      val merge = b.add(Merge[Many[I]](2))

      // unzipping Many into List[I] and Option[String] representing next records url.
      val unzip = b.add(UnzipWith((many: Many[I]) => (many.value, many.`@odata.nextLink`)))

      // in order for the cycle to complete we need to add takeWhile here. otherwise merge stage
      // will never be completed.
      val nextPagePath = Flow[Option[String]]
        .takeWhile(_.isDefined)
        .mapConcat(_.toList)
        .map(Office365Api.extractPath)

      // loading next page from the API
      val loadPage = Flow[String].flatMapConcat(path => client.getMany[I](path))

      val flatten = b.add(Flow[List[I]].mapConcat(identity))

      // the graph itself:
      // @formatter:off
      firstPage ~> merge             ~>                 unzip.in
                   merge <~ loadPage <~ nextPagePath <~ unzip.out1
                                                        unzip.out0 ~> flatten
      // @formatter:on

      SourceShape(flatten.out)
    })
  }

  private def pathFor[I: Api]: String = pathPrefix + implicitly[Api[I]].path

  private def schemaFor[I: Schema]: Schema[I] = implicitly[Schema[I]]

  private def queryParamsFor[I: Schema]: List[(String, String)] = queryParamsFor[I](null, null)

  private def queryParamsFor[I: Schema](filter: String, orderby: String): List[(String, String)] = {
    val schema = schemaFor[I]

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


object Office365Client {
  implicit def manyReads[I: Reads]: Reads[Many[I]] = Json.reads[Many[I]]
}

trait LowLevelClient {
  def getOne[I: Reads](path: String, queryParams: Seq[(String, String)]): Source[I, NotUsed]

  def getMany[I: Reads](path: String, queryParams: Seq[(String, String)] = Seq.empty): Source[Many[I], NotUsed]

  def close(): Unit
}

/**
  * Low-level API client.
  */
class Office365Client(baseUrl: String, ws: StandaloneWSClient, credentialData: CredentialData, preferredBodyType: BodyType)
  extends LowLevelClient {

  val tokenSource = TokenSource(credentialData)

  import Office365Client.manyReads

  override def getOne[I: Reads](path: String, queryParams: Seq[(String, String)]): Source[I, NotUsed] =
    prepareRequest(path)
      .via(withQueryParams(queryParams))
      .via(execute("GET"))
      .via(handle404)
      .via(parse[I])

  override def getMany[I: Reads](path: String, queryParams: Seq[(String, String)] = Seq.empty): Source[Many[I], NotUsed] =
    prepareRequest(path)
      .via(withQueryParams(queryParams))
      .via(execute("GET"))
      .via(parse[Many[I]])

  /**
    * Create an http request with common parameters (authentication, http headers).
    */
  private def prepareRequest(path: String): Source[Req, NotUsed] =
    tokenSource.credential
      .map(accessToken =>
        ws.url(s"$baseUrl$path")
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
  private def parse[I: Reads]: Flow[Resp, I, NotUsed] =
    Flow[Resp].map(_.body[JsValue].as[I])

  /**
    * Add query parameters to the request
    */
  private def withQueryParams(params: Seq[(String, String)]): Flow[Req, Req, NotUsed] =
    Flow[Req].map(_.withQueryStringParameters(params: _*))

  override def close(): Unit = tokenSource.close()
}

class Office365Exception(message: String) extends IOException(message)

case class Office365ResponseException(status: Int, statusText: String, errorDetails: String)
  extends Office365Exception(s"$status - $statusText: $errorDetails")

case class Many[I](value: List[I],
                   `@odata.context`: String,
                   `@odata.nextLink`: Option[String])

sealed abstract class BodyType(val name: String)

object BodyType {

  case object Text extends BodyType("text")

  case object Html extends BodyType("html")

}