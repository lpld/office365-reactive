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
import com.github.lpld.office365.model.{OMessage, Recipient}
import com.github.lpld.office365.Schema
import play.api.libs.json.{Json, Writes}

case class EmailMessage(Id: String,
                        Subject: String,
                        From: Option[Recipient],
                        ToRecipients: List[Recipient]) extends OMessage

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

Methods that represent queries to the API return `Source[I, NotUsed]`, where `I` is a type of requested items. No actuall http requests will be done until the source is materialzed.



#### Get an item by ID

```scala
// source containing zero or one element
val item: Source[EmailMessage, NotUsed] = api.get[EmailMessage](itemId)

item.runWith(...)
```

This will result in the following request:

```
 GET /messages/{itemId}?$select=Id,Subject,From,ToRecipients
```



#### Get multiple items:

```scala
val items: Source[EmailMessage, NotUsed] = 
  api.query[EmailMessage](
    filter = "ReceivedDateTime ge 2018-01-01T00:10:00Z",
    orderby = "ReceivedDateTime"
  )

items.runWith(...)
```

This will result in a series of requests to the API, each one loading the next page of items. The first request will look like:

```
GET /messages
	?$select=Id,Subject,From,ToRecipients
	&$filter=ReceivedDateTime ge 2018-01-01T00:10:00Z
	&$orderby=ReceivedDateTime
	&$top=100
	&$skip=0
```

This method is lazy, in a sense that it does not load additional pages until they are actually needed. It means that the following example will load only the first page of data even if more items are available:

```scala
val items = api.queryAll[EmailMessage]

items.take(1).runForeach(println)
```



#### Get items from a specific folder

```scala
import com.github.lpld.office365.model.{OEvent, FolderType}
import com.github.lpld.office365.Schema
import play.api.libs.json.Json

// Model for an event item:
case class Event(Id: String, Subject: String) extends OEvent
object Event {
  implicit val schema = Schema[Event]
  implicit val reads = Json.reads[Event]
}

// querying events from a specific calendar folder:
val calendarId = "<id of the calendar>"
val events = api
	.from(FolderType.Calendar, calendarId)
	.queryAll[Event]

events.runWith(...)
```

Following HTTP request will be executed:

```
GET /calendars/<id of the calendar>/events&$top=100&$skip=0
```

, followed by requests to load rest of the pages.



#### Get messages from a folder with a well-known name

```scala
import com.github.lpld.office365.model.WellKnownFolder

val items: Source[EmailMessage, NotUsed] =
	api
		.from(WellKnownFolder.SentItems) // mailbox folder with a well-known name
		.query[EmailMessage](
          filter = "ReceivedDateTime ge 2018-01-01T00:10:00Z"
        )

items.runWith(...)
```

Following request will be executed:

```
GET /mailfolders/SentItems/messages
	?$select=Id,Subject,From,ToRecipients
    &$filter=ReceivedDateTime ge 2018-01-01T00:10:00Z
    &$top=100
    &$skip=0
```



to be continued...