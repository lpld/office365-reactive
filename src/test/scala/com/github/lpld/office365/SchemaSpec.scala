package com.github.lpld.office365

import java.time.Instant

import com.github.lpld.office365.model.SingleValueProperty
import org.scalatest.{Matchers, WordSpec}

/**
  * @author leopold
  * @since 18/05/18
  */
class SchemaSpec extends WordSpec with Matchers {

  "Schema" should {
    "be generated from case class fields" in {

      val schema = Schema[TestModel]

      schema.extendedProperties shouldBe empty
      schema.standardProperties shouldEqual List("Id", "Subject", "ReceivedDateTime")
    }

    "ignore reserved property names" in {
      Schema[ExtendedTestModel].standardProperties shouldEqual List("Id")
    }
  }
}

case class TestModel(Id: String, Subject: String, ReceivedDateTime: Instant)

case class ExtendedTestModel(Id: String, SingleValueExtendedProperties: List[SingleValueProperty])
