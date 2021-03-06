import helper.TestValueStore
import play.api.Logger
import play.api.libs.json.{ JsObject, Json }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testHelper.{ Helper, StartedApp }
import testHelper.Helper._
import testHelper.TestConfig._

/**
 * User: Björn Reimer
 * Date: 30.07.14
 * Time: 15:26
 */
class UserNotificationSpec extends StartedApp {

  sequential

  "User Notifications" should {

    val mailInt1 = "devnull@cameo.de"
    val mailInt3 = "devnull2@cameo.de"
    val telInt2 = "+49134564"
    val telInt3 = "+491345644654"

    val mailExt1 = "devnull3@cameo.de"
    val mailExt3 = "devnull4@cameo.de"
    val telExt2 = "+491234"
    val telExt3 = "+494561"

    val testUser1 = TestUser.create(email = Some(mailInt1))
    val testUser2 = TestUser.create(tel = Some(telInt2))
    val testUser3 = TestUser.create(tel = Some(telInt3), email = Some(mailInt3))

    val externalContactIdentityId1 = testUser1.addExternalContact( email = Some(mailExt1))._2
    val externalContactIdentityId2 = testUser1.addExternalContact( tel = Some(telExt2))._2
    val externalContactIdentityId3 = testUser1.addExternalContact( tel = Some(telExt3), email = Some(mailExt3))._2

    testUser1. makeFriends(testUser2)
    testUser1.makeFriends( testUser3)

    var conversationId = ""
    "start conversation with external and internal contacts" in {
      val path = basePath + "/conversation"

      val recipients = Seq(externalContactIdentityId1, externalContactIdentityId2, externalContactIdentityId3, testUser2.identityId, testUser3.identityId)

      val json = Json.obj("recipients" -> recipients)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(tokenHeader(testUser1.token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      conversationId = (data \ "id").as[String]

      1 === 1
    }

    step(TestValueStore.start())

    "add message to conversation" in {
      val path = basePath + "/conversation/" + conversationId + "/message"

      val json = Json.obj("plain" -> Json.obj("text" -> "foo"))

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(testUser1.token)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(OK)
    }

    "all users except the sender should have received notifications" in {
      // wait until notifications have arrived
      Helper.waitFor(TestValueStore.getValues("mail").length == 1 && TestValueStore.getValues("sms").length == 4)

      val mails = TestValueStore.getValues("mail")
      val sms = TestValueStore.getValues("sms")

      sms.exists(js => (js \ "to").as[String].equals(telExt2)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telExt3)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telInt2)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telInt3)) must beTrue

      mails.exists(js => (js \ "to").as[String].equals(mailExt1)) must beTrue
      mails.exists(js => (js \ "to").as[String].equals(mailExt3)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailInt1)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailInt3)) must beFalse

      mails.length must beEqualTo(1)
      sms.length must beEqualTo(4)
    }

    var tokenExt1 = ""
    "get token for external user" in {
      // get purl
      val message = TestValueStore.getValues("mail").find(js => (js \ "to").as[String].equals(mailExt1)).get
      val purlExternal1 = (message \ "body").as[String].split("/p/")(1)

      // use purl to get token
      val path = basePath + "/purl/" + purlExternal1
      val req = FakeRequest(GET, path)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      tokenExt1 = (data \ "token").as[String]

      1 === 1
    }

    step(TestValueStore.stop())
    step(TestValueStore.start())

    "add event subscription to internal user" in {
      val path = basePath + "/eventSubscription"
      val req = FakeRequest(POST, path).withHeaders(tokenHeader(testUser2.token)).withJsonBody(Json.obj())

      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "add event subscription to external user" in {
      val path = basePath + "/eventSubscription"
      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExt1)).withJsonBody(Json.obj())

      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "send another message" in {
      val path = basePath + "/conversation/" + conversationId + "/message"

      val json = Json.obj("plain" -> Json.obj("text" -> "foo"))

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(testUser1.token)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(OK)
    }

    "users with event subscriptions should not get notifications" in {
      // wait until notifications have arrived
      Helper.waitFor(TestValueStore.getValues("mail").length == 0 && TestValueStore.getValues("sms").length == 3)

      val mails = TestValueStore.getValues("mail")
      val sms = TestValueStore.getValues("sms")

      sms.exists(js => (js \ "to").as[String].equals(telExt2)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telExt3)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telInt2)) must beFalse
      sms.exists(js => (js \ "to").as[String].equals(telInt3)) must beTrue

      mails.exists(js => (js \ "to").as[String].equals(mailExt1)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailExt3)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailInt1)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailInt3)) must beFalse

      mails.length must beEqualTo(0)
      sms.length must beEqualTo(3)
    }

    step(TestValueStore.stop())
    step(TestValueStore.start())

    "recipient send message" in {
      val path = basePath + "/conversation/" + conversationId + "/message"

      val json = Json.obj("plain" -> Json.obj("text" -> "foo"))

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(testUser2.token)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(OK)
    }

    "author of conversation should now receive a notification" in {
      // wait until notifications have arrived
      Helper.waitFor(TestValueStore.getValues("mail").length == 1 && TestValueStore.getValues("sms").length == 3)

      val mails = TestValueStore.getValues("mail")
      val sms = TestValueStore.getValues("sms")

      sms.exists(js => (js \ "to").as[String].equals(telExt2)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telExt3)) must beTrue
      sms.exists(js => (js \ "to").as[String].equals(telInt2)) must beFalse
      sms.exists(js => (js \ "to").as[String].equals(telInt3)) must beTrue

      mails.exists(js => (js \ "to").as[String].equals(mailExt1)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailExt3)) must beFalse
      mails.exists(js => (js \ "to").as[String].equals(mailInt1)) must beTrue
      mails.exists(js => (js \ "to").as[String].equals(mailInt3)) must beFalse

      mails.length must beEqualTo(1)
      sms.length must beEqualTo(3)
    }

    step(TestValueStore.stop())
  }

}
