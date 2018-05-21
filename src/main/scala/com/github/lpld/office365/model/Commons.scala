package com.github.lpld.office365.model

import com.github.lpld.office365.ExtendedProperty
import play.api.libs.json.Json

// Common objects:
case class ItemBody(ContentType: String, Content: String) extends Model
case class EmailAddress(Address: Option[String], Name: Option[String]) extends Model
case class Recipient(EmailAddress: EmailAddress) extends Model
case class SingleValueProperty(PropertyId: String, Value: String) extends Model

trait Model
object Model {
  implicit val body = Json.format[ItemBody]
  implicit val emailAddress = Json.format[EmailAddress]
  implicit val recipients = Json.format[Recipient]
  implicit val singleValueProperty = Json.format[SingleValueProperty]
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

sealed abstract class BodyType(val name: String)

object BodyType {
  case object Text extends BodyType("text")
  case object Html extends BodyType("html")
}
