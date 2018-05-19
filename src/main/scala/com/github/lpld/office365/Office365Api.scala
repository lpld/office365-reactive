package com.github.lpld.office365

import java.io.IOException

import akka.NotUsed
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source, UnzipWith}
import com.github.lpld.office365.model._
import play.api.libs.json._

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

  def apply(client: HttpClient,
            credential: CredentialData,
            defaultPageSize: Int = 100,
            preferredBodyType: BodyType = BodyType.Html) =
    new Office365Api(
      client,
      Office365Api.defaultUrl,
      credential,
      preferredBodyType,
      defaultPageSize
    )
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
class Office365Api(client: HttpClient,
                   baseUrl: String,
                   credential: CredentialData,
                   preferredBodyType: BodyType = BodyType.Html,
                   defaultPageSize: Int = 100) extends ItemBox[Folder] {

  private val tokenSource = TokenSource(credential)

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

  def close(): Unit = tokenSource.close()

  private def request[I: Reads](method: String)(path: String, queryParams: Seq[(String, String)]): Source[I, NotUsed] =
    tokenSource.credential
      .mapAsync(1)(accessToken =>
        client.request[I](
          url = s"$baseUrl$path",
          method = method,
          httpHeaders = Seq(
            "Authorization" -> s"Bearer $accessToken",
            "Accept" -> "application/json",
            "Prefer" -> s"""outlook.body-content-type="${preferredBodyType.name}""""
          ),
          queryParams = queryParams
        )
      )

  private def reqGet[I: Reads](path: String, queryParams: Seq[(String, String)] = Seq.empty): Source[I, NotUsed] =
    request("GET")(path, queryParams)

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

  private def extractPath(fullUrl: String): String = {
    require(fullUrl startsWith baseUrl)
    fullUrl substring baseUrl.length
  }

  private class ItemBoxImpl[F <: Folder](client: HttpClient, pathPrefix: String, defaultPageSize: Int = 100) extends ItemBox[F] {

    override def get[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](id: String): Source[I, NotUsed] =
      reqGet[I](s"${pathFor[I]}/$id", queryParamsFor[I])
        .via(handle404)

    override def query[I <: Item with ChildOf[_ <: F] : Schema : Reads : Api](filter: String, orderby: String): Source[I, NotUsed] =
      getPaged[I](queryParamsFor[I](filter, orderby): _*)

    /**
      * Perform a paginated query to the API.
      */
    def getPaged[I: Api : Reads](params: (String, String)*): Source[I, NotUsed] = {
      Source.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        // loading first page from the API
        val firstPage = reqGet[Many[I]](pathFor[I], params ++ List("$top" -> s"$defaultPageSize", "$skip" -> "0"))

        // merging results from firstPage and feedback loop
        val merge = b.add(Merge[Many[I]](2))

        // unzipping Many into List[I] and Option[String] representing next records url.
        val unzip = b.add(UnzipWith((many: Many[I]) => (many.value, many.`@odata.nextLink`)))

        // in order for the cycle to complete we need to add takeWhile here. otherwise merge stage
        // will never be completed.
        val nextPagePath = Flow[Option[String]]
          .takeWhile(_.isDefined)
          .mapConcat(_.toList)
          .map(extractPath)

        // loading next page from the API
        val loadPage = Flow[String].flatMapConcat(reqGet[Many[I]](_))

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