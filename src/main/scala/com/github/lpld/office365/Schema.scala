package com.github.lpld.office365

/**
  * @author leopold
  * @since 14/05/18
  */
abstract class Schema {
  private var properties: List[Property] = Nil

  protected def prop(name: String): Property = {
    val prop = StandardProperty(name)
    properties ::= prop
    prop
  }
}

object MessageSchema extends Schema {
  val Id = prop("Id")
  val BccRecipients = prop("BccRecipients")
  val Body = prop("Body")
  val BodyPreview = prop("BodyPreview")
  val Categories = prop("Categories")
  val CcRecipients = prop("CcRecipients")
  val ConversationId = prop("ConversationId")
  val CreatedDateTime = prop("CreatedDateTime")
  val From = prop("From")
  val HasAttachments = prop("HasAttachments")
  val IsDraft = prop("IsDraft")
  val LastModifiedDateTime = prop("LastModifiedDateTime")
  val ParentFolderId = prop("ParentFolderId")
  val ReceivedDateTime = prop("ReceivedDateTime")
  val ReplyTo = prop("ReplyTo")
  val Sender = prop("Sender")
  val SentDateTime = prop("SentDateTime")
  val Subject = prop("Subject")
  val ToRecipients = prop("ToRecipients")
}

abstract class Property(name: String)

case class StandardProperty(name: String) extends Property(name)

case class ExtendedProperty(name: String, definition: String) extends Property(name)
