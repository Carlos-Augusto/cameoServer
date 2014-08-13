package controllers

import helper.CheckHelper
import helper.ResultHelper._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Action
import traits.ExtendedController

/**
 * User: Michael Merz
 * Date: 31/01/14
 * Time: 4:30 PM
 */

object ServicesController extends ExtendedController {

  def checkPhoneNumber = Action(parse.tolerantJson) {
    request =>
      val jsBody: JsValue = request.body
      (jsBody \ "phoneNumber").asOpt[String] match {
        case Some(phoneNumber) => CheckHelper.checkAndCleanPhoneNumber(phoneNumber) match {
          case None    => resBadRequest("invalid phone number")
          case Some(p) => resOk(Json.obj("phoneNumber" -> p))
        }
        case None => resBadRequest("no phoneNumber")
      }
  }

  def checkEmailAddress = Action(parse.tolerantJson) {
    request =>
      val jsBody: JsValue = request.body
      (jsBody \ "emailAddress").asOpt[String] match {
        case Some(email) => CheckHelper.checkAndCleanEmailAddress(email) match {
          case None    => resBadRequest("invalid emailAddress")
          case Some(e) => resOk(Json.obj("email" -> e))
        }
        case None => resBadRequest("missing emailAddress")
      }
  }

  def getBrowserInfo() = Action {
    request =>
      val language = request.acceptLanguages.headOption match{
        case None => "en"
        case Some(lang) => lang.language
      }
      resOk(Json.obj("language" -> language))
  }
}
