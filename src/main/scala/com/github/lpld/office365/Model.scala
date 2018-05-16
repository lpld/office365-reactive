package com.github.lpld.office365

import java.time.Instant

import play.api.libs.json.Json

/**
  * @author leopold
  * @since 14/05/18
  */
class Entity[-T](val apiPath: String, val schema: Schema)

trait OMessage {
}

object Entity {

  implicit val messages: Entity[OMessage] = new Entity[OMessage]("/messages", MessageSchema)
}

// todo: add all properties
case class OMessageFull
(
  Id: String,
  Subject: String,
  ConversationId: String,
  ReceivedDateTime: Instant

) extends OMessage

object OMessageFull {
  implicit val reads = Json.reads[OMessageFull]
}

case class OMessageIdOnly(Id: String) extends OMessage

object OMessageIdOnly {
  implicit val reads = Json.reads[OMessageIdOnly]
}


