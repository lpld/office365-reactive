package com.github.lpld.office365

import scala.reflect.runtime.universe._

/**
  * Schema represents a set of properties of the entity [[T]]
  *
  * @author leopold
  * @since 14/05/18
  */
case class Schema[T](standardProperties: Seq[String], extendedProperties: List[ExtendedProperty])

object Schema {

  private val reservedPropertyNames = Set("SingleValueExtendedProperties")

  /**
    * Generate schema for a type [[T]]. List of standard properties will be automatically discovered from case-class
    * property names. Optionally, a list of extended properties can be passed.
    */
  def apply[T: TypeTag](extendedProperties: ExtendedProperty*): Schema[T] = {

    val stdProperties = typeOf[T]
      .members
      .collect {
        case m: MethodSymbol if m.isVal && m.isCaseAccessor =>
          m.name.decodedName.toString
      }
      .filterNot(reservedPropertyNames.contains)
      .toList

    Schema(stdProperties, extendedProperties.toList)
  }

  def apply[T: TypeTag]: Schema[T] = apply[T]()
}

case class ExtendedProperty(name: String, propertyId: String)
