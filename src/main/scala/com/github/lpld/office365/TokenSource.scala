package com.github.lpld.office365

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, Stash}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.github.lpld.office365.TokenRefresher.{GetToken, TokenResponse, TokenFailure, TokenSuccess}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class CredentialData(initialToken: Option[TokenSuccess], refreshAction: () => Future[TokenResponse])
                         (implicit val actorFactory: ActorRefFactory)

/**
  * A source for OAuth 2.0 access tokens, that can be shared between stream materializations.
  *
  * todo: think better of the situations when the `refresher` actor fails. Who should supervise it?
  *
  * @author leopold
  * @since 14/05/18
  */
class TokenSource(initialToken: Option[TokenSuccess],
                  refreshAction: () => Future[TokenResponse]
                 )(implicit actorFactory: ActorRefFactory) {

  // This actor actually holds the state (the latest valid token) and initiates
  // the refresh when needed.
  val refresher: ActorRef = actorFactory.actorOf(TokenRefresher(initialToken, refreshAction))

  /**
    * A source that, when materialized, will produce a single element: a valid, non-expired
    * access token.
    */
  val credential: Source[String, NotUsed] =
    Source
      .fromFuture {
        import akka.pattern.ask
        implicit val timeout: Timeout = 30.seconds
        (refresher ? GetToken).mapTo[TokenResponse]
      }
      .map {
        case TokenFailure(_, error) => throw TokenRefreshException(error)
        case TokenSuccess(token, _) => token
      }

  def close(): Unit = {
    actorFactory.stop(refresher)
  }
}

object TokenSource {
  def apply(credential: CredentialData) =
    new TokenSource(credential.initialToken, credential.refreshAction)(credential.actorFactory)
}

case class TokenRefreshException(error: String) extends Exception(error)

object TokenRefresher {

  sealed trait TokenResponse

  case class TokenSuccess(accessToken: String, expiresAt: Long) extends TokenResponse

  // "critical" means refresh token is not valid anymore, no further attempts will make sense
  case class TokenFailure(critical: Boolean, error: String) extends TokenResponse

  case object GetToken

  def apply(initialToken: Option[TokenSuccess], refreshAction: () => Future[TokenResponse]) =
    Props(classOf[TokenRefresher], initialToken, refreshAction, 3)
}

/**
  * Actor that holds the latest access token and performs refresh when needed.
  */
class TokenRefresher
(
  initialToken: Option[TokenSuccess],
  refreshAction: () => Future[TokenResponse],
  maxAttempts: Int
) extends Actor with Stash with ActorLogging {

  // creating fake expired token
  var tokenData: TokenResponse = initialToken.getOrElse(TokenSuccess("none", 0L))
  var attemptsPerformed = 0

  private def needRefresh: Boolean = tokenData match {
    case TokenSuccess(_, expiresAt) => System.currentTimeMillis() > expiresAt - 5.minutes.toMillis
    case TokenFailure(false, _) => attemptsPerformed < maxAttempts
    case TokenFailure(true, _) => false
  }

  override def receive: Receive = {
    case GetToken =>
      if (needRefresh) {
        stash()
        startRefresh()
        context.become(receiveWhileRefreshing, discardOld = false)
      } else {
        sender() ! tokenData
      }
  }

  def receiveWhileRefreshing: Receive = {
    case GetToken => stash() // this will be handled when the token is refreshed
    case response: TokenResponse =>
      handleRefreshResponse(response)
      if (needRefresh) startRefresh()
      else {
        unstashAll()
        context.unbecome()
      }
  }

  private def startRefresh(): Unit = {
    import akka.pattern.pipe
    implicit val ctx = context.dispatcher

    attemptsPerformed = attemptsPerformed + 1
    refreshAction() pipeTo self
  }

  private def handleRefreshResponse(response: TokenResponse): Unit = {
    this.tokenData = response

    if (tokenData.isInstanceOf[TokenSuccess]) {
      attemptsPerformed = 0
    }
  }

}
