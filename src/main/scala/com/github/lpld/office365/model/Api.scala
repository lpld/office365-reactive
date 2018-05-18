package com.github.lpld.office365.model

/**
  * Any API resource
  */
trait Resource

/**
  * Root resource: /
  */
trait Root extends Resource

/**
  * Item (entity with ID)
  */
trait Item extends Resource

/**
  * Folder. It can contain items, and it is an item itslef.
  */
trait Folder extends Item

/**
  * Specifies that a resource is a child of parent resource [[R]]
  */
trait ChildOf[R <: Resource] extends Resource

// Concrete API resources:

// @formatter:off
trait OMailFolder    extends Folder with ChildOf[Root]
trait OMessage       extends Item   with ChildOf[OMailFolder]

trait OCalendarGroup extends Folder with ChildOf[Root]
trait OCalendar      extends Folder with ChildOf[OCalendarGroup]
trait OEvent         extends Item   with ChildOf[OCalendar]
trait OCalendarView  extends Item   with ChildOf[OCalendar]

trait OContactFolder extends Folder with ChildOf[Root]
trait OContact       extends Item   with ChildOf[OContactFolder]

trait OTaskGroup     extends Folder with ChildOf[Root]
trait OTaskFolder    extends Folder with ChildOf[OTaskGroup]
trait OTask          extends Item   with ChildOf[OTaskFolder]
// @formatter:on

case class Api[-T](path: String)

object Api {
  implicit val root = Api[Root]("")

  implicit val mailfolders = Api[OMailFolder]("/mailfolders")
  implicit val messages = Api[OMessage]("/messages")

  implicit val calendargroups = Api[OCalendarGroup]("calendargroups")
  implicit val calendars = Api[OCalendar]("/calendars")
  implicit val events = Api[OEvent]("/events")
  implicit val calendarviews = Api[OCalendarView]("/calendarviews")

  implicit val contactfolders = Api[OContactFolder]("/contactfolders")
  implicit val contacts = Api[OContact]("/contacts")

  implicit val taskgroups = Api[OTaskGroup]("/taskgroups")
  implicit val taskfolders = Api[OTaskFolder]("/taskfolders")
  implicit val tasks = Api[OTask]("/tasks")
}

sealed trait FolderType[F <: Folder]
object FolderType {
  case object MailFolder extends FolderType[OMailFolder]
  case object TaskFolder extends FolderType[OTaskFolder]
  case object Calendar extends FolderType[OCalendar]
  case object ContactFolder extends FolderType[OContactFolder]
}

sealed abstract class WellKnownFolder(val name: String)
object WellKnownFolder {
  case object Inbox extends WellKnownFolder("Inbox")
  case object Drafts extends WellKnownFolder("Drafts")
  case object SentItems extends WellKnownFolder("SentItems")
  case object DeletedItems extends WellKnownFolder("DeletedItems")
  case object AllItems extends WellKnownFolder("AllItems")
}

