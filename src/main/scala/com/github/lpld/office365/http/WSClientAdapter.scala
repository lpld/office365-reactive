package com.github.lpld.office365.http

import java.io.{Closeable, IOException}

import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest}

import scala.language.implicitConversions

/**
  * Play's WSClient (non-standalone) doesn't extend [[play.api.libs.ws.StandaloneWSClient]], so this trait is just
  * an adapter for them to share a common interface.
  *
  * @author leopold
  * @since 14/05/18
  */
trait WSClientAdapter extends Closeable {

  @throws[IllegalArgumentException]
  def url(url: String): StandaloneWSRequest

  @throws[IOException]
  def close(): Unit
}

object WSClientAdapter {

  implicit def standaloneClient2Adapter(standaloneClient: StandaloneWSClient): WSClientAdapter =
    new WSClientAdapter {
      override def url(url: String): StandaloneWSRequest = standaloneClient.url(url)

      override def close(): Unit = standaloneClient.close()
    }
}
