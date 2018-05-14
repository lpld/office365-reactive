package com.github.lpld.office365

/**
  * @author leopold
  * @since 14/05/18
  */
class Entity[T](val apiPath: String)

trait OMessage {
}

object Entities {
  implicit val message = new Entity[OMessage]("/messages")
}


