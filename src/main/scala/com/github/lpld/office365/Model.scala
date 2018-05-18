package com.github.lpld.office365

import java.time.Instant

import play.api.libs.json.Json

/**
  * @author leopold
  * @since 14/05/18
  */
case class Path[-T](apiPath: String)

object Path {
  implicit val root = Path[ORoot]("")
  implicit val messages = Path[OMessage]("/messages")
  implicit val contacts = Path[OContact]("/contacts")
  implicit val events = Path[OEvent]("/events")
  implicit val calendarviews = Path[OCalendarView]("/calendarviews")
  implicit val tasks = Path[OCalendarView]("/tasks")

  implicit val mailfolders = Path[OMailFolder]("/mailfolders")
  implicit val contactfolders = Path[OContactFolder]("/contactfolders")
  implicit val calendars = Path[OCalendar]("/calendars")
  implicit val taskfolders = Path[OContactFolder]("/taskfolders")
}

trait Model

trait Item[F <: OFolder] extends Model

trait OMessage extends Item[OMailFolder]
trait OContact extends Item[OContactFolder]
trait OEvent extends Item[OCalendar]
trait OCalendarView extends Item[OCalendar]
trait OTask extends Item[OTaskFolder]

trait OFolder
trait ORoot extends OFolder
trait OMailFolder extends Item[ORoot] with OFolder
trait OContactFolder extends Item[ORoot] with OFolder
trait OTaskFolder extends Item[ORoot] with OFolder
trait OCalendar extends Item[ORoot] with OFolder

// Common objects:
case class ItemBody(ContentType: String, Content: String) extends Model
case class EmailAddress(Address: Option[String], Name: Option[String]) extends Model
case class Recipient(EmailAddress: EmailAddress) extends Model
case class SingleValueProperty(PropertyId: String, Value: String) extends Model

object Model {
  implicit val bodyReads = Json.reads[ItemBody]
  implicit val emailAddressReads = Json.reads[EmailAddress]
  implicit val recipientsReads = Json.reads[Recipient]
  implicit val singleValuePropertyReads = Json.reads[SingleValueProperty]
}

/**
  * Extended properties support
  */
trait ExtendedPropertiesSupport {
  def SingleValueExtendedProperties: List[SingleValueProperty]

  private lazy val singleValueProps = SingleValueExtendedProperties
    .groupBy(_.PropertyId)
    .mapValues(_.head.Value)

  def getProp(prop: ExtendedProperty): Option[String] = singleValueProps.get(prop.propertyId)
}

case class Message
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
object Message {
  implicit val reads = Json.reads[Message]
  implicit val schema = Schema[Message]
}

case class MessageIdOnly(Id: String) extends OMessage
object MessageIdOnly {
  implicit val reads = Json.reads[MessageIdOnly]
  implicit val schema = Schema[MessageIdOnly]
}

sealed trait Folder[F <: OFolder]

object Folder {

  case object MailFolder extends Folder[OMailFolder]
  case object TaskFolder extends Folder[OTaskFolder]
  case object Calendar extends Folder[OCalendar]
  case object ContactFolder extends Folder[OContactFolder]

}

sealed abstract class WellKnownFolder(val name: String)

object WellKnownFolder {

  case object Inbox extends WellKnownFolder("Inbox")
  case object Drafts extends WellKnownFolder("Drafts")
  case object SentItems extends WellKnownFolder("SentItems")
  case object DeletedItems extends WellKnownFolder("DeletedItems")
  case object AllItems extends WellKnownFolder("AllItems")

}

case class EventIdOnly(Id: String) extends OEvent

object EventIdOnly {
  implicit val reads = Json.reads[EventIdOnly]
  implicit val schema = Schema[EventIdOnly]
}