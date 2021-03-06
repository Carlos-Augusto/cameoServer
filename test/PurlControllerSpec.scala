import play.api.libs.json.{ JsArray, Json, JsObject }
import play.api.test.{ FakeRequest, FakeApplication }
import play.api.test.Helpers._
import scala.Some
import testHelper.Helper._
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.api.Play.current
import play.api.Logger
import testHelper.{ StartedApp, Helper }
import org.specs2.mutable._
import testHelper.TestConfig._

/**
 * User: Björn Reimer
 * Date: 3/3/14
 * Time: 4:48 PM
 */
class PurlControllerSpec extends StartedApp {

  sequential


  var purlExternToken = ""

  "PurlController" should {

    "get purl object for external user" in {

      val path = basePath + "/purl/" + purlExtern

      val req = FakeRequest(GET, path)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "conversation").asOpt[JsObject] must beSome
      val conversation = (data \ "conversation").as[JsObject]
      (data \ "identity").asOpt[JsObject] must beSome
      val identity = (data \ "identity").as[JsObject]
      (data \ "token").asOpt[String] must beSome
      purlExternToken = (data \ "token").as[String]

      (conversation \ "id").asOpt[String] must beSome(purlConversationId)
      (conversation \ "recipients").asOpt[Seq[JsObject]] must beSome
      val r = (conversation \ "recipients")(0).as[JsObject]
      (r \ "identityId").asOpt[String] must beSome
      (identity \ "id").asOpt[String] must beSome(purlExternIdentitityId)
    }

    "refuse to return purl object to external user that is not part of the conversation any more" in {

      val path = basePath + "/purl/" + purlExternInvalid

      val req = FakeRequest(GET, path)
      val res = route(req).get

      status(res) must equalTo(UNAUTHORIZED)
    }

    "get purl object for external user with token" in {
      val path = basePath + "/purl/" + purlExtern

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "conversation").asOpt[JsObject] must beSome
      val conversation = (data \ "conversation").as[JsObject]
      (conversation \ "id").asOpt[String] must beSome(purlConversationId)
      (data \ "identity").asOpt[JsObject] must beSome
      val identity = (data \ "identity").as[JsObject]
      (identity \ "id").asOpt[String] must beSome(purlExternIdentitityId)
      (data \ "token").asOpt[String] must beSome(purlExternToken)
    }

    "get purl object for external user with other token" in {
      val path = basePath + "/purl/" + purlExtern

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "conversation").asOpt[JsObject] must beSome
      val conversation = (data \ "conversation").as[JsObject]
      (conversation \ "id").asOpt[String] must beSome(purlConversationId)
      (data \ "identity").asOpt[JsObject] must beSome
      val identity = (data \ "identity").as[JsObject]
      (identity \ "id").asOpt[String] must beSome(purlExternIdentitityId)
      (data \ "token").asOpt[String] must beSome(purlExternToken)
    }



    "get purl object of internal user with token" in {

      val path = basePath + "/purl/" + purlIntern

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "conversation").asOpt[JsObject] must beSome
      val conversation = (data \ "conversation").as[JsObject]
      (conversation \ "id").asOpt[String] must beSome(purlConversationId)

      (data \ "identity").asOpt[JsObject] must beSome
      val identity = (data \ "identity").as[JsObject]
      (identity \ "id").asOpt[String] must beSome(identityExisting2)

      (data \ "token").asOpt[String] must beNone
    }

    "refuse to get purl object of internal user with wrong token" in {

      val path = basePath + "/purl/" + purlIntern

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(UNAUTHORIZED)

    }

    "refuse to get purl object of internal user with token of other member of the conversation" in {

      val path = basePath + "/purl/" + purlIntern

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternToken))
      val res = route(req).get

      status(res) must equalTo(UNAUTHORIZED)
    }

    "refuse to get purl object of internal user without token" in {

      val path = basePath + "/purl/" + purlIntern

      val req = FakeRequest(GET, path)
      val res = route(req).get

      status(res) must equalTo(UNAUTHORIZED)
    }


  }
}