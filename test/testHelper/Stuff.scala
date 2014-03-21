package testHelper

import play.api.libs.json.{Json, JsObject}
import helper.TestHelper

/**
 * User: Björn Reimer
 * Date: 1/30/14
 * Time: 2:19 PM
 */
object Stuff {

  def createUser(login: String, password: String, cameoId: String, tel: Option[String] = None, email: Option[String] = None): JsObject = {
    Json.obj(
      "loginName" -> login,
      "password" -> password,
      "cameoId" -> cameoId) ++ {
      if(tel.isDefined) Json.obj("phoneNumber" -> tel.get) else Json.obj() } ++ {
      if(email.isDefined) Json.obj("email" -> email.get) else Json.obj() }
  }

  val random = new scala.util.Random

  def randomString(n: Int): String = {
    def alphabet: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    //TODO check whether random.setSeed is needed
    Stream.continually(random.nextInt(alphabet.size)).map(alphabet).take(n).mkString
  }

  def randomLengthString(max: Int) = {
    val n = random.nextInt(max)
    val nonZero = if (n == 0) 1 else n
    randomString(nonZero)
  }

  def tokenHeader(token: String): (String, String) = ("Authorization", token)

  def twoFactorTokenHeader(token: String): (String, String) = ("X-TwoFactorToken", token)




}
