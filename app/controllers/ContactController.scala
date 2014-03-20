package controllers

import traits.ExtendedController

import play.api.libs.json._
import models.{ ContactUpdate, MongoId, Identity, Contact }
import helper.{ OutputLimits, AuthAction }
import scala.concurrent.{ ExecutionContext, Future }
import helper.ResultHelper._
import scala.Some
import ExecutionContext.Implicits.global
import constants.Contacts._
import play.api.mvc.SimpleResult
import play.api.Logger

/**
 * User: Björn Reimer
 * Date: 6/14/13
 * Time: 5:06 PM
 */
object ContactController extends ExtendedController {

  def addContact() = AuthAction.async(parse.tolerantJson) {
    request =>

      def addExternalContact(js: JsObject): Future[SimpleResult] = {
        validateFuture(js, Identity.createReads) { identity =>
          Identity.col.insert(identity).flatMap { error =>
            error.ok match {
              case false => Future(resServerError("could not save new identity"))
              case true  => createContact(identity.id, CONTACT_TYPE_EXTERNAL)
            }
          }
        }
      }

      def addInternalContact(identityId: String): Future[SimpleResult] = {
        // check if the user already has this contact
        request.identity.contacts.exists(_.identityId.toString.equals(identityId)) match {
          case true => Future(resKO("identity is already in address book"))
          case false =>
            // check if identity exists
            Identity.find(new MongoId(identityId)).flatMap {
              case None => Future(resNotFound("identity"))
              case Some(i) =>
                createContact(i.id, CONTACT_TYPE_INTERNAL)
            }
        }
      }

      def createContact(identityId: MongoId, contactType: String): Future[SimpleResult] = {
        validateFuture(request.body, Contact.createReads(identityId, contactType)) {
          contact =>
            {
              request.identity.addContact(contact)
              contact.toJsonWithIdentity.map(js => resOK(js))
            }
        }
      }

      // check if the contact is internal or external
      (request.body \ "identityId").asOpt[String] match {
        case Some(id) => addInternalContact(id)
        case None =>
          (request.body \ "identity").asOpt[JsObject] match {
            case None     => Future(resBadRequest("no identityId or identity object"))
            case Some(js) => addExternalContact(js)
          }
      }

  }

  def editContact(contactId: String) = AuthAction(parse.tolerantJson) {
    request =>
      val res = request.identity.contacts.find(contact => contact.id.toString.equals(contactId))

      res match {
        case None => resNotFound("contact")
        case Some(contact) => {
          validate(request.body, ContactUpdate.format) {
            contactUpdate =>
              // if the contact is internal we can only change the groups
              if (contact.contactType.equals(CONTACT_TYPE_INTERNAL) &&
                (contactUpdate.email.isDefined || contactUpdate.phoneNumber.isDefined || contactUpdate.displayName.isDefined)) {
                resUnauthorized("cannot change contact details of another cameo user")
              } else {
                contact.update(contactUpdate)
                resOK()
              }
          }
        }
      }
  }

  def getContact(contactId: String) = AuthAction.async {
    request =>
      val res = request.identity.contacts.find(contact => contact.id.toString.equals(contactId))

      res match {
        case None          => Future(resNotFound("contact"))
        case Some(contact) => contact.toJsonWithIdentityResult
      }
  }

  def getContacts(offset: Int, limit: Int) = AuthAction.async {
    request =>
      val contacts = OutputLimits.applyLimits(request.identity.contacts, offset, limit)

      Future.sequence(contacts.map(_.toJsonWithIdentity)).map {
        c => resOK(c)
      }
  }

  def deleteContact(contactId: String) = AuthAction.async {
    request =>
      val res = request.identity.contacts.find(contact => contact.id.toString.equals(contactId))

      res match {
        case None => Future(resNotFound("contact"))
        case Some(c) =>
          request.identity.deleteContact(c.id).map {
            case false => resBadRequest("unable to delete")
            case true  => resOK("deleted")
          }
      }
  }

  def getGroup(group: String, offset: Int, limit: Int) = AuthAction.async {
    request =>

      val contacts = request.identity.getGroup(group)
      val limited = OutputLimits.applyLimits(contacts, offset, limit)

      Future.sequence(limited.map(_.toJsonWithIdentity)).map {
        c => resOK(c)
      }
  }

  def getGroups = AuthAction {
    request =>
      val groups = request.identity.getGroups
      resOK(Json.toJson(groups))
  }

  def getFriendRequests = AuthAction.async {
    request =>
      val futureFriendRequests = request.identity.friendRequests.map {
        id =>
          Identity.find(id).map {
            case None    => Json.obj()
            case Some(i) => i.toPublicSummaryJson
          }
      }

      Future.sequence(futureFriendRequests).map {
        seq => resOK(seq)
      }
  }

  case class SendFriendRequest(identityId: Option[String],
                               cameoId: Option[String])

  object SendFriendRequest {
    implicit val reads: Reads[SendFriendRequest] = Json.reads[SendFriendRequest]
  }

  def sendFriendRequest = AuthAction.async(parse.tolerantJson) {
    request =>
      def executeFriendRequest(receiver: MongoId): Future[SimpleResult] = {
        // check if the other identity is already in contact
        request.identity.contacts.exists(c => {
          if (c.identityId.equals(receiver)) {
            Logger.debug("CONTACT:" + c.identityId.toJson + " COMPARE: " + receiver.toJson)
            true
          } else {
            false
          }
        }) match {
          case true => Future(resKO("identity is already in address book"))
          case false =>
            // check if identityId exists
            Identity.find(receiver).flatMap {
              case None => Future(resNotFound("identity"))
              case Some(other) => other.addFriendRequest(request.identity.id).map {
                lastError =>
                  if (lastError.updatedExisting) {
                    resOK()
                  } else {
                    resServerError("could not update")
                  }
              }
            }
        }
      }

      validateFuture(request.body, SendFriendRequest.reads) {
        sfr =>
          (sfr.identityId, sfr.cameoId) match {
            case (None, None)            => Future(resBadRequest("either identityId or cameoId required"))
            case (Some(i), Some(c))      => Future(resBadRequest("only identityId or cameoId allowed"))
            case (Some(i: String), None) => executeFriendRequest(new MongoId(i))
            case (None, Some(c: String)) => {
              // search for cameoId and get identityId
              Identity.findCameoId(c).flatMap {
                case None           => Future(resNotFound("cameoId"))
                case Some(identity) => executeFriendRequest(identity.id)
              }
            }
          }
      }
  }

  case class AnswerFriendRequest(identityId: String, answerType: String)

  object AnswerFriendRequest { implicit val format = Json.format[AnswerFriendRequest] }

  def answerFriendRequest = AuthAction.async(parse.tolerantJson) {
    request =>
      validateFuture(request.body, AnswerFriendRequest.format) {
        afr =>
          request.identity.friendRequests.find(_.id.equals(afr.identityId)) match {
            case None => Future(resBadRequest("no friendRequest from this identityId"))
            case Some(o) => afr.answerType match {
              case FRIEND_REQUEST_REJECT => request.identity.deleteFriendRequest(new MongoId(afr.identityId)).map {
                lastError => if (lastError.updatedExisting) resOK() else resServerError("unable to delete")
              }
              case FRIEND_REQUEST_ACCEPT =>
                // add contact to both identites
                request.identity.deleteFriendRequest(new MongoId(afr.identityId))
                Identity.find(afr.identityId).flatMap {
                  case None => Future(resNotFound("other identity"))
                  case Some(otherIdentity) =>
                    for {
                      le1 <- otherIdentity.addContact(Contact.create(request.identity.id, CONTACT_TYPE_INTERNAL))
                      le2 <- request.identity.addContact(Contact.create(otherIdentity.id, CONTACT_TYPE_INTERNAL))
                    } yield {
                      le1 && le2 match {
                        case true  => resOK("added contacts")
                        case false => resKO("duplicate entries")
                      }
                    }
                }
              case _ => Future(resBadRequest("invalid answer type"))
            }
          }
      }
  }
}
