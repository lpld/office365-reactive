package com.github.lpld.office365.model

import java.time.Instant

import com.github.lpld.office365.Schema
import play.api.libs.json.Json

/**
  * @author leopold
  * @since 18/05/18
  */
case class MessageFull
(
  Id: String,
  BccRecipients: List[Recipient],
  Body: ItemBody,
  BodyPreview: String,
  Categories: List[String],
  CcRecipients: List[Recipient],
  ConversationId: String,
  CreatedDateTime: Instant,
  From: Recipient,
  HasAttachments: Boolean,
  Importance: String,
  IsDraft: Boolean,
  IsRead: Boolean,
  LastModifiedDateTime: Instant,
  ParentFolderId: String,
  ReceivedDateTime: Instant,
  ReplyTo: List[Recipient],
  Sender: Recipient,
  SentDateTime: Instant,
  Subject: String,
  ToRecipients: List[Recipient],
  UniqueBody: ItemBody

) extends OMessage

object MessageFull {
  implicit val reads = Json.reads[MessageFull]
  implicit val schema = Schema[MessageFull]
}

case class MessageIdOnly(Id: String) extends OMessage
object MessageIdOnly {
  implicit val reads = Json.reads[MessageIdOnly]
  implicit val schema = Schema[MessageIdOnly]
}

