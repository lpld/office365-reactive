package com.github.lpld.office365

import java.io.IOException

import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.libs.ws._

import scala.concurrent.Future
import scala.language.implicitConversions

/**
  * Http-level abstraction, that makes [[Office365Api]] independent from Http client implementation and
  * simplifies testing. [[Office365Api]], however, still depends on Play's json.Reads.
  *
  * @author leopold
  * @since 19/05/18
  */
trait HttpClient {
  def request[OUT: Out, IN: In](url: String,
                                method: String,
                                httpHeaders: Seq[(String, String)],
                                queryParams: Seq[(String, String)],
                                payload: OUT = ()): Future[IN]
}

/**
  * Deserializer for http response.
  */
trait In[T] {
  def parse(r: StandaloneWSResponse): T
}

object In {

  import JsonBodyReadables._

  implicit val unitIn: In[Unit] = _ => ()
  implicit def readsToIn[T](implicit reads: Reads[T]): In[T] = _.body[JsValue].as[T]
}

/**
  * Serializer for http request payload.
  */
trait Out[-T] {
  def toBody(t: T): WSBody
}

object Out {

  implicit val unitOut: Out[Unit] = _ => EmptyBody
  implicit def writesToOut[T](implicit writes: Writes[T]): Out[T] =
    t => JsonBodyWritables.writeableOf_JsValue.transform(Json.toJson(t))
}

class PlayWsHttpClient(ws: StandaloneWSClient) extends HttpClient {

  implicit val jsonContentBodyWritable: BodyWritable[WSBody] = {
    BodyWritable(identity, "application/json")
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  override def request[OUT: Out, IN: In](url: String,
                                         method: String,
                                         httpHeaders: Seq[(String, String)],
                                         queryParams: Seq[(String, String)],
                                         payload: OUT): Future[IN] =
    ws.url(url)
      .withHttpHeaders(httpHeaders: _*)
      .addHttpHeaders("Content-Type" -> "application/json")
      .withQueryStringParameters(queryParams: _*)
      .withBody(implicitly[Out[OUT]].toBody(payload))
      .execute(method)
      .collect {
        case resp if resp.status >= 200 && resp.status < 300 => resp
        case failed => throw HttpResponseException(failed.status, failed.statusText, failed.body)
      }
      .map(implicitly[In[IN]].parse(_))

}

case class HttpResponseException(status: Int, statusText: String, errorDetails: String)
  extends IOException(s"$status - $statusText: $errorDetails")
