package com.github.lpld.office365.model

import com.github.lpld.office365.Schema
import play.api.libs.json.Json

/**
  * @author leopold
  * @since 18/05/18
  */
case class EventFull(Id: String) extends OEvent

case class EventIdOnly(Id: String) extends OEvent

object EventIdOnly {
  implicit val reads = Json.reads[EventIdOnly]
  implicit val schema = Schema[EventIdOnly]
}

