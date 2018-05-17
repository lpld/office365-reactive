package com.github.lpld.office365

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import com.github.lpld.office365.TokenRefresher.{TokenFailure, TokenSuccess}
import org.scalatest.{BeforeAndAfterAll, LoneElement, Matchers, WordSpecLike}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationDouble, FiniteDuration}

/**
  * @author leopold
  * @since 17/05/18
  */
class TokenSourceSpec extends TestKit(ActorSystem("token-source-test"))
  with WordSpecLike
  with Matchers
  with LoneElement
  with BeforeAndAfterAll {

  implicit val materializer = ActorMaterializer()

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Token source" should {
    "use initial token if not expired" in {

      val source = TokenSource(CredentialData(
        Some(tokenSuccess("abc", expiresIn = 10.minutes)),
        () => Future(fail("Should not be called"))(ExecutionContext.global)
      ))

      getCredential(source) shouldEqual "abc"
    }

    "refresh if initial token is not provided" in {
      val source = TokenSource(CredentialData(
        None,
        () => Future.successful(tokenSuccess("xyz", expiresIn = 10.minutes))
      ))

      getCredential(source) shouldEqual "xyz"
    }

    "refresh if initial token is expiring soon" in {
      val source = TokenSource(CredentialData(
        Some(tokenSuccess("abc", expiresIn = 1.minute)),
        () => Future.successful(tokenSuccess("xyz", expiresIn = 10.minutes))
      ))

      getCredential(source) shouldEqual "xyz"
    }

    "fail if token refresh fails" in {
      val source = TokenSource(CredentialData(
        None,
        () => Future.successful(TokenFailure(critical = true, "Refresh failure"))
      ))

      a[TokenRefreshException] should be thrownBy getCredential(source)
    }

    "refresh token only after it expires" in {
      val source = TokenSource(CredentialData(
        Some(tokenSuccess("abc", expiresIn = 5.minutes + 3.seconds)),
        () => Future.successful(tokenSuccess("xyz", expiresIn = 10.minutes))
      ))

      getCredential(source) shouldEqual "abc"
      Thread.sleep(3.seconds.toMillis)
      getCredential(source) shouldEqual "xyz"
    }
  }

  private def tokenSuccess(token: String, expiresIn: FiniteDuration) =
    TokenSuccess(token, System.currentTimeMillis() + expiresIn.toMillis)

  private def getCredential(source: TokenSource) =
    Await.result(
      source.credential
        .recover { case e => throw e }
        .runWith(Sink.seq),

      1.second
    ).loneElement
}
