package com.github.lpld.office365

import java.time.{Duration, Instant}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.util.Failure

/**
  * @author leopold
  * @since 15/05/18
  */
object Office365Access extends App {

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  val api = new Office365Api(
    ws = StandaloneAhcWSClient(),
    credential = Credential(
      "aaa",
      "xxx"
    ),
    defaultPageSize = 5
  )

  import com.github.lpld.office365.Entity.messages
  import scala.concurrent.ExecutionContext.Implicits.global

  val messages: Source[OMessageFull, _] = api.query[OMessageFull](
    $select = "Id,Subject,ConversationId,ReceivedDateTime",
    $filter = s"ReceivedDateTime ge ${Instant.now() minus Duration.ofDays(30)}",
    $orderby = "ReceivedDateTime"
  )

  messages
    .runForeach(println)
    .onComplete {
      case Failure(ex) => ex.printStackTrace()
      case _ => println("Done")
    }

  val messageIds = api.query[OMessageIdOnly](
    $select = "Id",
    $filter = s"ReceivedDateTime ge ${Instant.now() minus Duration.ofDays(30)}",
    $orderby = "ReceivedDateTime"
  )

  messageIds
    .runForeach(println)
    .onComplete {
      case Failure(ex) => ex.printStackTrace()
      case _ => println("Done")
    }
}
