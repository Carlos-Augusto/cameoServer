package controllers

import traits.ExtendedController
import models._
import play.api.mvc.Action
import scala.concurrent.{ Future, ExecutionContext }
import ExecutionContext.Implicits.global
import helper.ResultHelper._
import helper.AuthAction
import scala.Some
import play.api.libs.json._
import scala.Some
import helper.JsonHelper._
import scala.Some

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._

/**
 * User: Björn Reimer
 * Date: 1/20/14
 * Time: 12:07 PM
 */
object IdentityController extends ExtendedController {

  def getIdentityById(id: String) = AuthAction.async {

    val mongoId = new MongoId(id)

    Identity.find(mongoId).map {
      case None           => resNotFound("identity")
      case Some(identity) => resOK(identity.toJson)
    }
  }

  def getIdentityByToken = AuthAction.async {
    request =>

      val mongoId = request.identity.id

      Identity.find(mongoId).map {
        case None           => resNotFound("identity")
        case Some(identity) => resOK(identity.toJson)
      }
  }

  case class IdentityUpdate(phoneNumber: Option[String],
                            email: Option[String],
                            displayName: Option[String])

  object IdentityUpdate {
    implicit val format: Format[IdentityUpdate] = Json.format[IdentityUpdate]
  }

  def updateIdentity() = AuthAction.async(parse.tolerantJson) {
    request =>
      validateFuture[IdentityUpdate](request.body, IdentityUpdate.format) {
        update =>
          {

            val newMail = update.email.flatMap { getNewValueVerifiedString(request.identity.email, _) }
            val newPhoneNumber = update.phoneNumber.flatMap { getNewValueVerifiedString(request.identity.phoneNumber, _) }
            val newDisplayName = update.displayName.flatMap { getNewValueString(request.identity.displayName, _) }

            request.identity.update(email = newMail, phoneNumber = newPhoneNumber, displayName = newDisplayName).flatMap {
              lastError =>
                {
                  Identity.find(request.identity.id).map {
                    case None    => resNotFound("identity")
                    case Some(i) => resOK(i.toJson)
                  }
                }

            }
          }
      }
  }

  def search() = Action.async(parse.tolerantJson) {

    request =>

      case class VerifyRequest(cameoId: String)

      def reads: Reads[VerifyRequest] =
        (__ \ 'cameoId).read[String](minLength[String](4)).map {
          l => VerifyRequest(l)
        }

      validateFuture(request.body, reads) {
        vr =>
          Identity.matchCameoId(vr.cameoId).map {
            list => resOK(list.map { i => JsString(i.cameoId) })
          }
      }
  }
}
