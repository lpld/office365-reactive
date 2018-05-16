package com.github.lpld.office365

import java.time.Instant

import play.api.libs.json.Json

/**
  * @author leopold
  * @since 14/05/18
  */
case class Path[-T](apiPath: String)

object Path {
  implicit val messages = Path[OMessage]("/messages")
  implicit val folders = Path[OMailFolder]("/folders")
}

trait Model

trait Entity extends Model

trait OMessage extends Entity
trait OMailFolder extends Entity

// Common objects:
case class Body(ContentType: String, Content: String) extends Model
case class EmailAddress(Address: Option[String], Name: Option[String]) extends Model
case class EmailContact(EmailAddress: EmailAddress) extends Model
case class SingleValueProperty(PropertyId: String, Value: String) extends Model

object Model {
  implicit val bodyReads = Json.reads[Body]
  implicit val emailAddressReads = Json.reads[EmailAddress]
  implicit val emailContactReads = Json.reads[EmailContact]
  implicit val singleValuePropertyReads = Json.reads[SingleValueProperty]
}

/**
  * Extended properties support
  */
trait ExtendedPropertiesSupport {
  protected def SingleValueExtendedProperties: List[SingleValueProperty]

  private lazy val singleValueProps = SingleValueExtendedProperties
    .groupBy(_.PropertyId)
    .mapValues(_.head.Value)

  def getProp(prop: ExtendedProperty): Option[String] = singleValueProps.get(prop.propertyId)
}

// todo: add all properties
case class Message
(
  Id: String,
  Subject: String,
  ConversationId: String,
  ReceivedDateTime: Instant

) extends OMessage

object Message {
  implicit val reads = Json.reads[Message]
  implicit val schema = Schema[Message]
}

case class MessageIdOnly(Id: String) extends OMessage

object MessageIdOnly {
  implicit val reads = Json.reads[MessageIdOnly]
  implicit val schema = Schema[MessageIdOnly]
}
