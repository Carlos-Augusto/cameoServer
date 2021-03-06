package legacy


import org.specs2.matcher.MatchResult
import play.api.Play.current
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Logger, Play}
import testHelper.Helper._
import testHelper.TestConfig._
import testHelper.{StartedApp, TestConfig}

/**
 * User: Björn Reimer
 * Date: 3/3/14
 * Time: 4:48 PM
 */
class AccountControllerV1Spec extends StartedApp {

  sequential

  val login = randomString(8)
  val login2 = randomString(8)
  val loginExternal = randomString(8)
  val pass = randomString(8)
  val displayName = "MOEP"
  val mail = validEmails(0)
  val tel = validPhoneNumbers(0)._1
  val cleanedTel = validPhoneNumbers(0)._2
  val displayName2 = "MOEP2"
  val mail2 = validEmails(1)
  val tel2 = validPhoneNumbers(1)._2
  var identityId = ""
  var token = ""
  var regSec = ""
  var regSec2 = ""

  "legacy AccountController" should {

    "Reserve Login" in {
      val path = basePath + "/account/check"
      val json = Json.obj("loginName" -> login)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      val regSeqOpt = (data \ "reservationSecret").asOpt[String]

      if (regSeqOpt.isDefined) {
        regSec = regSeqOpt.get
      }

      regSeqOpt aka "returned registration secret" must beSome
    }

    "Refuse to claim reserved login without secret" in {
      val path = basePath + "/account"
      val json = createUser(login, pass)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    "Refuse to create account with invalid mails" in {
      val path = basePath + "/account"

      TestConfig.invalidEmails.map { invalid =>

        val json = createUser(login, pass, Some(tel), Some(invalid)) ++ Json.obj("reservationSecret" -> regSec)

        val req = FakeRequest(POST, path).withJsonBody(json)
        val res = route(req).get

        status(res) must equalTo(BAD_REQUEST)
      }
    }

    "Refuse to create account with invalid phoneNumbers" in {
      val path = basePath + "/account"

      TestConfig.invalidPhoneNumbers.map { invalid =>

        val json = createUser(login, pass, Some(invalid), Some(mail)) ++ Json.obj("reservationSecret" -> regSec)

        val req = FakeRequest(POST, path).withJsonBody(json)
        val res = route(req).get

        status(res) must equalTo(BAD_REQUEST)
      }
    }

    "Refuse to register with wrong loginName for secret" in {
      val path = basePath + "/account"
      val json = createUser(login + "a", pass) ++ Json.obj("reservationSecret" -> regSec)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    "Create Account" in {
      val path = basePath + "/account"
      val json = createUser(login, pass, Some(tel), Some(mail)) ++
        Json.obj("reservationSecret" -> regSec) ++
        Json.obj("displayName" -> displayName)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      val identity = (data \ "identities")(0).as[JsObject]

      val identityOpt = (identity \ "id").asOpt[String]

      if (identityOpt.isDefined) {
        identityId = identityOpt.get
      }

      identityOpt must beSome
      (data \ "id").asOpt[String] must beSome

      (identity \ "id").asOpt[String] must beSome
      (identity \ "displayName").asOpt[String] must beSome(displayName)
      (identity \ "userKey").asOpt[String] must beSome
    }

    "Refuse to register again with same secret" in {
      val path = basePath + "/account"
      val json = createUser(login + "a", pass) ++ Json.obj("reservationSecret" -> regSec)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    "Return a token" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      val tokenOpt = (data \ "token").asOpt[String]
      if (tokenOpt.isDefined) {
        token = tokenOpt.get
      }

      tokenOpt must beSome
    }

    "Return a token and ignore capitalization of loginName" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login.toUpperCase + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      (data \ "token").asOpt[String] must beSome
    }

    "Automatically create an identity for a new account" in {
      val path = basePath + "/identity"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      (data \ "userKey").asOpt[String] must beSome
      (data \ "cameoId").asOpt[String] must beSome(login + "@" + domain)
      (data \ "displayName").asOpt[String] must beSome(displayName)
      (data \ "email" \ "value").asOpt[String] must beNone
      (data \ "phoneNumber" \ "value").asOpt[String] must beNone
    }

    var fileId = ""
    "automatically create avatar for new identity" in {
      val path = basePath + "/identity"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "avatar").asOpt[String] must beSome
      fileId = (data \ "avatar").as[String]
      1 === 1
    }

    "check that avatar file meta exist" in {

      val path = basePath + "/file/" + fileId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(fileId)
      (data \ "chunks").asOpt[Seq[Int]] must beSome
      (data \ "chunks")(0).asOpt[Int] must beSome(0)
      (data \ "chunks")(1).asOpt[Int] must beNone
      (data \ "fileName").asOpt[String] must beSome("avatar.png")
      (data \ "fileSize").asOpt[Int] must beSome
      (data \ "fileType").asOpt[String] must beSome("image/png")
    }

    "check that avatar file chunk exists" in {
      val path = basePath + "/file/" + fileId + "/" + 0

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
      val raw = contentAsBytes(res)

      raw.length must beGreaterThan(100)
    }

    "automatically add support as contact" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(1)

      (data(0) \ "identityId").asOpt[String] must beEqualTo(Play.configuration.getString("support.contact.identityId"))
    }

    var conversationId = ""
    "automatically add talk with support" in {
      val path = basePath + "/conversations"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      val conversations = (data \ "conversations").as[Seq[JsObject]]

      conversations.length must beEqualTo(1)
      (conversations(0) \ "subject").asOpt[String] must beEqualTo(Play.configuration.getString("support.conversation.subject"))

      val message = (conversations(0) \ "messages")(0).as[JsObject]
      (message \ "plain" \ "text").asOpt[String] must beEqualTo(Play.configuration.getString("support.conversation.body"))
    }

    var externalToken = ""
    "get purl object for external user" in {

      val path = basePath + "/purl/" + purlExtern3

      val req = FakeRequest(GET, path)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      externalToken = (data \ "token").as[String]

      1 === 1
    }

    var regSecExternal = ""
    "Reserve Login for external user" in {
      val path = basePath + "/account/check"
      val json = Json.obj("loginName" -> loginExternal)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      regSecExternal = (data \ "reservationSecret").as[String]

      1 === 1
    }

    "register user with token of external user" in {
      val path = basePath + "/account"
      val json = createUser(loginExternal, pass, Some(tel2), Some(mail2)) ++
        Json.obj("reservationSecret" -> regSecExternal) ++
        Json.obj("displayName" -> displayName2)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(tokenHeader(externalToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      val identity = (data \ "identities")(0).as[JsObject]

      (identity \ "id").asOpt[String] must beSome(purlExtern3IdentitityId)
      (identity \ "phoneNumber" \ "value").asOpt[String] must beNone
      (identity \ "email" \ "value").asOpt[String] must beNone
    }

    var purlExternIdentityToken = ""
    "get token of new account" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((loginExternal + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      (contentAsJson(res) \ "data" \ "token").asOpt[String] must beSome
      purlExternIdentityToken = (contentAsJson(res) \ "data" \ "token").as[String]

      1 === 1
    }

    "get identity of new account" in {

      val path = basePath + "/identity/" + purlExtern3IdentitityId

      val req = FakeRequest(GET, path)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(purlExtern3IdentitityId)
      (data \ "cameoId").asOpt[String] must beSome(loginExternal + "@" + domain)
      (data \ "avatar").asOpt[String] must beSome
    }

    "identity should have sender as contact" in {

      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternIdentityToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(2)

      data.find(js => (js \ "identityId").asOpt[String].equals(Some(identityExisting))) must beSome
    }

    "identity should have support contact" in {

      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternIdentityToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(2)

      data.find(js => (js \ "identityId").asOpt[String].equals(Play.configuration.getString("support.contact.identityId"))) must beSome
    }

    "identity should have support conversation" in {

      val path = basePath + "/conversations"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternIdentityToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data" \ "conversations").as[Seq[JsObject]]

      data.length must beEqualTo(2)
    }
  }
}