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


