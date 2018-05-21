# reactive-office365

**Office365 Rest API** via **Akka Streams** and **Standalone Play WS client**.

Currently, only a limited set of features is supported.

## Quick start

#### Authentication

In order to use `Office365Api`, you must have a valid OAuth 2.0 access token and a way to refresh it. Create an instance of `CredentialData` first:

```scala
import com.github.lpld.office365.CredentialData
import com.github.lpld.office365.TokenRefresher.{TokenResponse, TokenSuccess}

import scala.concurrent.Future

// OAuth 2.0 credential
val credential = CredentialData(
  initialToken = getAccessToken,
  refreshAction = refreshAccessToken
)

// get existing access token, may return None:
def getAccessToken: Option[TokenSuccess] = ???
  
// perform OAuth 2.0 token refresh flow:
def refreshAccessToken(): Future[TokenResponse] = ???
```

Now you can create an instance of `Office365Api`:

```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.ahc.StandaloneAhcWSClient

// first, create actor system and akka-streams materializer
implicit val system = ActorSystem("api-examples")
implicit val materializer = ActorMaterializer()

val wsClient: StandaloneWSClient = StandaloneAhcWSClient()
val api = Office365Api(wsClient, credential)

// ... do your work

// close all resources:
api.close()
system.terminate()
```

## Defining a model

Define a case class with a set of fields that are needed. It should extend one of the traits that represent standard Outlook item types: `OMailFolder, OMessage, OCalendarGroup, OCalendar, OEvent, OCalendarView, OCalendarView, OContact, OTaskGroup, OTaskFolder, OTask`:

```scala
import com.github.lpld.office365.model.OMessage
import com.github.lpld.office365.Schema
import play.api.libs.json.{Json, Writes}

case class EmailMessage(Id: String, Subject: String) extends OMessage

// companion object with the Schema and Play's json Reads/Writes:
object EmailMessage {
  // A Schema is just a set of fields
  implicit val schema = Schema[EmailMessage] // this will generate a schema from the class fields
  
  // Play's Reads
  implicit val reads = Json.reads[EmailMessage]
}
```

The list of standard fields can be found in the official Office365 API documentation: https://msdn.microsoft.com/en-us/office/office365/api/api-catalog

## Querying the API

1. Getting item by ID:

```scala
item: Source[EmailMessage, NotUsed] = api.get[EmailMessage](itemId)

item.runWith(...)
```

This will result in the following request: `GET /messages/{itemId}?$select=Id,Subject`

2. Getting multiple items:

```scala
items: Source[EmailMessage, NotUsed] = 
  api.query[EmailMessage](
    filter = "ReceivedDateTime ge '2018-01-01T00:10:00Z'",
    orderby = "ReceivedDateTime"
  )

items.runWith(...)
```

This will result in a series of requests to the API, each one loading the next page of items. The first request will look like:

```
GET /messages
	?$select=Id,Subject
	&$filter=ReceivedDateTime ge '2018-01-01T00:10:00Z'
	&orderby=ReceivedDateTime
	&top=100
	&skip=0
```

to be continued...