package com.github.lpld.office365

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import com.github.lpld.office365.TokenRefresher.{TokenFailure, TokenResponse, TokenSuccess}
import org.scalatest.{BeforeAndAfterAll, LoneElement, Matchers, WordSpecLike}

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.util.Success

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

    "fail if token refresh fails with exception" in {

      val source = TokenSource(CredentialData(
        None,
        () => Future.failed(new RuntimeException("error"))
      ))

      a[TokenRefreshException] should be thrownBy getCredential(source)
    }

    "refresh token only after it expires" in {
      val source = TokenSource(CredentialData(
        Some(tokenSuccess("abc", expiresIn = 5.minutes + 1.second)),
        () => Future.successful(tokenSuccess("xyz", expiresIn = 10.minutes))
      ))

      getCredential(source) shouldEqual "abc"
      Thread.sleep(1.second.toMillis)
      getCredential(source) shouldEqual "xyz"
    }

    "refresh token only once even when parallel requests are performed" in {

      val refreshPromise = Promise[TokenResponse]

      val helper = TokenHelper(refreshPromise)

      val source = TokenSource(CredentialData(None, () => helper.refreshToken()))

      // two parallel materializations of the same source
      val token1 = runSource(source)
      val token2 = runSource(source)

      Thread.sleep(100)

      // means that `refresh` function was called
      helper.verifyAllRefreshRequestsArePerformed

      // not finished yet, because promise is not completed
      token1.isCompleted shouldBe false
      token2.isCompleted shouldBe false

      refreshPromise.complete(Success(tokenSuccess("xyz", 10.minutes)))

      // value should appear in both futures
      awaitCredential(token1) shouldEqual "xyz"
      awaitCredential(token2) shouldEqual "xyz"

      // one more:
      getCredential(source) shouldEqual "xyz"

      // verify that no more `refresh` requests were performed
      helper.verifyAllRefreshRequestsArePerformed
    }

    "should not perform retries if critical failure occurs" in {

      val helper = TokenHelper(Promise.successful[TokenResponse](TokenFailure(critical = true, "")))
      val source = TokenSource(CredentialData(None, () => helper.refreshToken()))

      a[TokenRefreshException] should be thrownBy getCredential(source)

      helper.verifyAllRefreshRequestsArePerformed
    }

    "should retry if non-critical failure occurs" in {
      val helper = TokenHelper(
        Promise.successful[TokenResponse](TokenFailure(critical = false, "")),
        Promise.successful[TokenResponse](tokenSuccess("ccc", 10.minutes))
      )
      val source = TokenSource(CredentialData(None, () => helper.refreshToken()))

      getCredential(source) shouldEqual "ccc"
      helper.verifyAllRefreshRequestsArePerformed
    }

    "should retry no more than 2 times" in {

      val helper = TokenHelper(
        Promise.successful[TokenResponse](TokenFailure(critical = false, "")),
        Promise.successful[TokenResponse](TokenFailure(critical = false, ""))
      )

      val source = TokenSource(CredentialData(None, () => helper.refreshToken()))
      a[TokenRefreshException] should be thrownBy getCredential(source)

      helper.verifyAllRefreshRequestsArePerformed
    }
  }

  case class TokenHelper(promises: Promise[TokenResponse]*) {
    private var refreshRequestsExceeded = false
    val responses: mutable.Queue[Promise[TokenResponse]] = mutable.Queue(promises: _*)

    def verifyAllRefreshRequestsArePerformed: Unit = {
      refreshRequestsExceeded shouldBe false
      responses shouldBe empty
    }

    def refreshToken(): Future[TokenResponse] =
      if (responses.isEmpty) {
        refreshRequestsExceeded = true
        Future.failed(new IllegalStateException("Refresh requests exceeded"))
      } else responses.dequeue().future
  }

  private def tokenSuccess(token: String, expiresIn: FiniteDuration) =
    TokenSuccess(token, System.currentTimeMillis() + expiresIn.toMillis)

  private def getCredential(source: TokenSource) = awaitCredential(runSource(source))

  private def awaitCredential(future: Future[Seq[String]]): String =
    Await.result(future, 1.second).loneElement

  private def runSource(source: TokenSource) =
    source.credential
      .recover { case e => throw e }
      .runWith(Sink.seq)

}
