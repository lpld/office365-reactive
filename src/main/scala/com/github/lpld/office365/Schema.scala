package com.github.lpld.office365

/**
  * @author leopold
  * @since 14/05/18
  */
abstract class Schema {
  private var fields: List[String] = Nil

  protected def field(name: String): String = {
    fields ::= name
    name
  }
}

object MessageSchema extends Schema {
  val Id = "Id"
  val BccRecipients = "BccRecipients"
  val Body = "Body"
  val BodyPreview = "BodyPreview"
  val Categories = "Categories"
  val CcRecipients = "CcRecipients"
  val ConversationId = "ConversationId"
  val CreatedDateTime = "CreatedDateTime"
  val From = "From"
  val HasAttachments = "HasAttachments"
  val IsDraft = "IsDraft"
  val LastModifiedDateTime = "LastModifiedDateTime"
  val ParentFolderId = "ParentFolderId"
  val ReceivedDateTime = "ReceivedDateTime"
  val ReplyTo = "ReplyTo"
  val Sender = "Sender"
  val SentDateTime = "SentDateTime"
  val Subject = "Subject"
  val ToRecipients = "ToRecipients"
}

