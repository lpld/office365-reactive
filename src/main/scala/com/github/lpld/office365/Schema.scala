package com.github.lpld.office365

import scala.reflect.runtime.universe._

/**
  * Schema represents a set of properties of the item [[I]]
  *
  * @author leopold
  * @since 14/05/18
  */
case class Schema[I](standardProperties: List[String], extendedProperties: List[ExtendedProperty])

object Schema {

  private val reservedPropertyNames = Set("SingleValueExtendedProperties")

  /**
    * Generate schema for a type [[I]]. List of standard properties will be automatically discovered from case-class
    * property names. Optionally, a list of extended properties can be passed.
    */
  def apply[I: TypeTag](extendedProperties: ExtendedProperty*): Schema[I] = {

    val stdProperties =
      typeOf[I]
        .members.sorted
        .collect { case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString }
        .filterNot(reservedPropertyNames.contains)

    Schema(stdProperties, extendedProperties.toList)
  }

  def apply[I: TypeTag]: Schema[I] = apply[I]()
}

case class ExtendedProperty(name: String, propertyId: String)
