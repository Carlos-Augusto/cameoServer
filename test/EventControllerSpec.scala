import actors.NewMessage
import play.api.libs.json.{ Json, JsObject }
import play.api.{ Logger, Play }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import scala.annotation.tailrec
import scala.concurrent.Future
import testHelper.{ TestConfig, StartedApp }
import testHelper.Stuff._
import testHelper.TestConfig._
import play.api.Play.current

/**
 * User: Björn Reimer
 * Date: 09.05.14
 * Time: 13:58
 */
class EventControllerSpec extends StartedApp {

  sequential

  var subscriptionId = ""
  var subscription2Id = ""

  "EventController" should {

    "Get a new subscription Id" in {
      val path = basePath + "/eventSubscription"

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      subscriptionId = (data \ "id").as[String]
      (data \ "events").asOpt[Seq[JsObject]] must beSome(haveLength[Seq[JsObject]](0))
    }

    "Get events" in {
      val path = basePath + "/eventSubscription/" + subscriptionId
      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(subscriptionId)
      (data \ "events").asOpt[Seq[JsObject]] must beSome(haveLength[Seq[JsObject]](0))
    }

    "Only allow limited amount per user" in {
      val max = Play.configuration.getInt("events.subscription.user.limit").get + 1
      val path = basePath + "/eventSubscription"
      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting2))

      (1 to max).seq.foreach { i =>
        val res = route(req).get
        i match {
          case j if j >= max => status(res) must equalTo(BAD_REQUEST)
          case _             => status(res) must equalTo(OK)
        }
      }

      1 === 1
    }

    "Get another event subscription" in {
      val path = basePath + "/eventSubscription"

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      subscription2Id = (data \ "id").as[String]
      (data \ "events").asOpt[Seq[JsObject]] must beSome(haveLength[Seq[JsObject]](0))
    }

    val numberOfMessages = 3
    val text = "the FooBaaMoep"
    var conversationId = ""
    "Create conversation and send some messages" in {

      // create conversation
      val path = basePath + "/conversation"
      val req = FakeRequest(POST, path).withJsonBody(Json.obj()).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get
      status(res) must equalTo(OK)
      conversationId = (contentAsJson(res) \ "data" \ "id").as[String]

      // send messages
      (1 to numberOfMessages).map { i =>
        val path2 = basePath + "/conversation/" + conversationId + "/message"
        val json2 = Json.obj("plain" -> Json.obj("text" -> text))
        val req2 = FakeRequest(POST, path2).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json2)
        val res2 = route(req2).get
        status(res2) must equalTo(OK)
      }
    }

    "Events should appear in both subscriptions" in {

      Seq(subscriptionId, subscription2Id).seq.map { id =>
        val path = basePath + "/eventSubscription/" + id
        val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
        val res = route(req).get

        status(res) must equalTo(OK)

        val data = (contentAsJson(res) \ "data").as[JsObject]

        (data \ "id").asOpt[String] must beSome(id)
        (data \ "events").asOpt[Seq[JsObject]] must beSome(haveLength[Seq[JsObject]](3))

        val events = (data \ "events").as[Seq[JsObject]]

        val newMessageEvents = events.filter(e =>
          (e \ "type").as[String].equals("conversation:new-message") &&
            (e \ "content" \ "conversationId").asOpt[String].getOrElse("foo").equals(conversationId))
        newMessageEvents.length must greaterThanOrEqualTo(3)
        newMessageEvents.map { js =>
          (js \ "content" \ "conversationId").asOpt[String] must beSome(conversationId)
          (js \ "content" \ "message").asOpt[JsObject] must beSome
          (js \ "content" \ "message" \ "plain" \ "text").asOpt[String] must beSome(text)
        }
      }
      1 === 1
    }

    "Events should be cleared" in {
      Seq(subscriptionId, subscription2Id).seq.map { id =>
        val path = basePath + "/eventSubscription/" + id
        val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
        val res = route(req).get

        status(res) must equalTo(OK)

        val data = (contentAsJson(res) \ "data").as[JsObject]
        (data \ "id").asOpt[String] must beSome(id)
        (data \ "events").asOpt[Seq[JsObject]] must beSome(haveLength[Seq[JsObject]](0))
      }

    }
  }

}
