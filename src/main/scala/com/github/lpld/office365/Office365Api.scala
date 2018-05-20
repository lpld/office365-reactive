package com.github.lpld.office365

import java.io.IOException

import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source, UnzipWith}
import com.github.lpld.office365.Office365Api.queryParamsFor
import com.github.lpld.office365.model._
import play.api.libs.json._

import scala.concurrent.Future

object Office365Api {
  val defaultUrl = "https://outlook.office.com/api/v2.0/me"

  def apply(httpClient: HttpClient,
            credential: CredentialData,
            defaultPageSize: Int = 100,
            preferredBodyType: BodyType = BodyType.Html) =
    new Office365Api(
      httpClient,
      credential,
      Office365Api.defaultUrl,
      defaultPageSize,
      preferredBodyType
    )

  def schemaFor[I: Schema]: Schema[I] = implicitly[Schema[I]]

  def queryParamsFor[I: Schema]: List[(String, String)] = queryParamsFor[I](null, null)

  def queryParamsFor[I: Schema](filter: String, orderby: String): List[(String, String)] = {
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

  implicit val unitReads = Reads { _ => JsSuccess(()) }
}

/**
  * API client, that takes care of API paths, item schemas and pagination. Generally, should be
  * created using companion object.
  *
  * It extends {{{ItemBox[Folder]}}}, which basically means that it's a storage for items of all possible types.
  *
  * @author leopold
  * @since 14/05/18
  */
class Office365Api private(lowLevelClient: LowLevelClient, defaultPageSize: Int)
  extends ItemBox[Folder](lowLevelClient, "", defaultPageSize) {

  def this(httpClient: HttpClient, credential: CredentialData,
           baseUrl: String, defaultPageSize: Int = 100, preferredBodyType: BodyType = BodyType.Html) =
    this(new LowLevelClient(httpClient, credential, baseUrl, preferredBodyType), defaultPageSize)

  /**
    * Get item by ID
    */
  def get[I <: Item : Schema : Reads : Api](id: String): Source[I, NotUsed] =
    lowLevelClient
      .get[I](s"${implicitly[Api[I]].path}/$id", queryParamsFor[I])
      .via(handle404)

  /**
    * Return ItemBox for a specific folder
    */
  def from[F <: Folder : Api](folderType: FolderType[F], id: String): ItemBox[F] =
    new ItemBox[F](lowLevelClient, s"${implicitly[Api[F]].path}/$id", defaultPageSize)

  /**
    * Return ItemBox for mail folder with well-known name
    */
  def from(wellKnownFolder: WellKnownFolder): ItemBox[OMailFolder] = from(FolderType.MailFolder, wellKnownFolder.name)

  private implicit val ur = Office365Api.unitReads

  /**
    * Send a message
    * todo: not tested
    */
  def sendmail[M <: OMessage : Writes](message: M, saveToSentItems: Boolean = true) =
    lowLevelClient.post[SendMessage[M], Unit]("/sendmail", SendMessage(message, saveToSentItems))


  def close(): Unit = lowLevelClient.close()

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
}

private class LowLevelClient(httpClient: HttpClient,
                             credential: CredentialData,
                             baseUrl: String,
                             preferredBodyType: BodyType) {

  def get[I: Reads](path: String, queryParams: Seq[(String, String)] = Seq.empty): Source[I, NotUsed] =
    request("GET", path, queryParams)

  def post[OUT: Writes, IN: Reads](path: String, payload: OUT): Source[IN, NotUsed] =
    request("POST", path, Nil, payload)

  //  def patch[I: Reads](path: String, queryParams: Seq[(String, String)] = Seq.empty): Source[I, NotUsed] =
  //    request("PATCH")(path, queryParams)

  def extractPath(fullUrl: String): String = {
    require(fullUrl startsWith baseUrl)
    fullUrl substring baseUrl.length
  }

  private val tokenSource = TokenSource(credential)
  def close(): Unit = tokenSource.close()

  private def request[IN: Reads](method: String, path: String, queryParams: Seq[(String, String)]): Source[IN, NotUsed] =
    makeRequest(headers => {
      httpClient.request[IN](
        url = s"$baseUrl$path",
        method = method,
        httpHeaders = headers,
        queryParams = queryParams
      )
    })

  private def request[OUT: Writes, IN: Reads](method: String, path: String, queryParams: Seq[(String, String)], out: OUT): Source[IN, NotUsed] =
    makeRequest(headers => {
      httpClient.request[OUT, IN](
        url = s"$baseUrl$path",
        method = method,
        httpHeaders = headers,
        queryParams = queryParams,
        out
      )
    })

  private def makeRequest[T](f: Seq[(String, String)] => Future[T]): Source[T, NotUsed] =
    tokenSource.credential
      .mapAsync(1)(accessToken => {
        val httpHeaders = Seq(
          "Authorization" -> s"Bearer $accessToken",
          "Accept" -> "application/json",
          "Prefer" -> s"""outlook.body-content-type="${preferredBodyType.name}""""
        )

        f(httpHeaders)
      })

}

/**
  * A part of the API that contains some items in it. For example:
  *  - / (root)
  *  - /Mailfolders/Inbox
  *  - /Calendars/AAMkAGI2TG93AAA=
  * etc.
  *
  * It supports basic operations for manipulating items (for now, only retrieving)
  */
class ItemBox[F <: Folder](client: LowLevelClient, pathPrefix: String, defaultPageSize: Int = 100) {

  /**
    * Query multiple items, optionally specifying filter and orderby parameters.
    */
  def query[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](filter: String = null, orderby: String = null): Source[I, NotUsed] =
    getPaged[I](queryParamsFor[I](filter, orderby): _*)

  /**
    * Query all items.
    */
  def queryAll[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api]: Source[I, NotUsed] = query(null, null)

  /**
    * Perform a paginated query to the API.
    */
  private def getPaged[I: Api : Reads](params: (String, String)*): Source[I, NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // loading first page from the API
      val firstPage = client.get[Many[I]](pathFor[I], params ++ List("$top" -> s"$defaultPageSize", "$skip" -> "0"))

      // merging results from firstPage and feedback loop
      val merge = b.add(Merge[Many[I]](2))

      // unzipping Many into List[I] and Option[String] representing next records url.
      val unzip = b.add(UnzipWith((many: Many[I]) => (many.value, many.`@odata.nextLink`)))

      // in order for the cycle to complete we need to add takeWhile here. otherwise merge stage
      // will never be completed.
      val nextPagePath = Flow[Option[String]]
        .takeWhile(_.isDefined)
        .mapConcat(_.toList)
        .map(client.extractPath)

      // loading next page from the API
      val loadPage = Flow[String].flatMapConcat(client.get[Many[I]](_))

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
}
class Office365Exception(message: String) extends IOException(message)

case class Office365ResponseException(status: Int, statusText: String, errorDetails: String)
  extends Office365Exception(s"$status - $statusText: $errorDetails")

case class Many[I](value: List[I],
                   `@odata.context`: String,
                   `@odata.nextLink`: Option[String])

object Many {
  implicit def manyReads[I: Reads]: Reads[Many[I]] = Json.reads[Many[I]]
}
sealed abstract class BodyType(val name: String)

object BodyType {

  case object Text extends BodyType("text")

  case object Html extends BodyType("html")

}