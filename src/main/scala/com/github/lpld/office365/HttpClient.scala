package com.github.lpld.office365

import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest}
import play.api.libs.ws.JsonBodyWritables._

import scala.concurrent.Future

/**
  * Http-level abstraction, that makes [[Office365Api]] independent from Http client implementation and
  * simplifies testing. [[Office365Api]], however, still depends on Play's json.Reads.
  *
  * @author leopold
  * @since 19/05/18
  */
trait HttpClient {
  def request[I: Reads](url: String,
                        method: String,
                        httpHeaders: Seq[(String, String)],
                        queryParams: Seq[(String, String)]): Future[I]

  def request[OUT: Writes, IN: Reads](url: String,
                                      method: String,
                                      httpHeaders: Seq[(String, String)],
                                      queryParams: Seq[(String, String)],
                                      payload: OUT): Future[IN]
}

class PlayWsHttpClient(ws: StandaloneWSClient) extends HttpClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def request[I: Reads](url: String, method: String, httpHeaders: Seq[(String, String)], queryParams: Seq[(String, String)]): Future[I] =
    executeAndParse(method, prepareRequest(url, httpHeaders, queryParams))

  override def request[OUT: Writes, IN: Reads](url: String,
                                               method: String,
                                               httpHeaders: Seq[(String, String)],
                                               queryParams: Seq[(String, String)],
                                               payload: OUT): Future[IN] =

    executeAndParse(
      method,
      prepareRequest(url, httpHeaders, queryParams).withBody(Json.toJson(payload))
    )

  private def prepareRequest(url: String, httpHeaders: Seq[(String, String)], queryParams: Seq[(String, String)]): StandaloneWSRequest = {
    ws.url(url)
      .withHttpHeaders(httpHeaders: _*)
      .withQueryStringParameters(queryParams: _*)
  }

  private def executeAndParse[I: Reads](method: String, req: StandaloneWSRequest): Future[I] =
    req.execute(method)
      .collect {
        case resp if resp.status >= 200 && resp.status < 300 => resp
        case failed => throw Office365ResponseException(failed.status, failed.statusText, failed.body)
      }
      .map(_.body[JsValue].as[I])

}
