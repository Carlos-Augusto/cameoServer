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
import reactivemongo.core.commands.LastError
import play.api.mvc.SimpleResult

/**
 * User: Björn Reimer
 * Date: 6/14/13
 * Time: 5:06 PM
 */
object ContactController extends ExtendedController {

  def addContact() = AuthAction.async(parse.tolerantJson) {
    request =>
      val jsBody: JsValue = request.body

      // check if an identity id is given
      val (contactType, maybeIdentity): (String, Future[Option[(Identity)]]) = (jsBody \ "identityId").asOpt[String] match {
        case Some(id) => {
          // check if identity exists
          (CONTACT_TYPE_INTERNAL, Identity.find(id))
        }
        case None => {
          // if not check if there is a valid identity object
          val i = (jsBody \ "identity").validate[Identity](Identity.createReads).map {
            identity =>
              {
                Identity.col.insert(identity).map {
                  lastError => Some(identity)
                }
              }
          }.recoverTotal(e => Future(None))
          (CONTACT_TYPE_EXTERNAL, i)
        }

      }

      // read contact and add identity id
      maybeIdentity.flatMap {
        case None => Future(resBadRequest("invalid identity"))
        case Some(identity) => {
          validateFuture(jsBody, Contact.createReads(identity.id, contactType)) {
            contact =>
              {
                request.identity.addContact(contact)
                contact.toJsonWithIdentity.map(js => resOK(js))
              }
          }
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
              }
              else {
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
            case Some(i) => i.toSummaryJson
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
      validateFuture(request.body, SendFriendRequest.reads) {
        case (None, None)            => Future(resBadRequest("either identityId or cameoId required"))
        case (Some(i), Some(c))      => Future(resBadRequest("only one identityId or cameoId allowed"))
        case (Some(i: String), None) => executeFriendRequest(new MongoId(i))
        case (None, Some(c: String)) => {
          // search for cameoId and get identityId
          Identity.findCameoId(c).flatMap {
            case None           => Future(resNotFound("cameoId"))
            case Some(identity) => executeFriendRequest(identity.id)
          }
        }
      }

      def executeFriendRequest(receiver: MongoId): Future[SimpleResult] = {
        // check if identityId exists
        Identity.find(receiver).flatMap {
          case None => Future(resNotFound("identity"))
          case Some(i) => i.addFriendRequest(request.identity.id).map {
            lastError =>
              if (lastError.updatedExisting) {
                resOK()
              }
              else {
                resServerError("could not update")
              }
          }
        }
      }
  }

  def anwserFriendRequest(id: String) = AuthAction.async(parse.tolerantJson) {
    request =>
      // check if id exists
      request.identity.friendRequests.find(mid => mid.id.equals(id)) match {
        case None => Future(resNotFound("requestId"))
        case Some(otherIdentityId) =>
          (request.body \ "type").asOpt[String] match {
            case None => Future(resBadRequest("no answer type"))
            case Some(FRIEND_REQUEST_REJECT) => request.identity.removeFriendRequest(new MongoId(id)).map {
              lastError => if (lastError.updatedExisting) resOK() else resServerError("unable to delete")
            }
            case Some(FRIEND_REQUEST_ACCEPT) => {
              // add contact to both identites
              Identity.find(otherIdentityId).flatMap {
                case None => Future(resNotFound("other identity"))
                case Some(otherIdentity) =>
                  for {
                    le1 <- otherIdentity.addContact(Contact.create(request.identity.id, CONTACT_TYPE_INTERNAL))
                    le2 <- request.identity.addContact(Contact.create(otherIdentity.id, CONTACT_TYPE_INTERNAL))
                  } yield {
                    le1.updatedExisting && le2.updatedExisting match {
                      case true  => resOK("added contacts")
                      case false => resServerError("error adding contacts")
                    }
                  }
              }
            }
            case _ => Future(resBadRequest("invalid awnser type"))
          }
      }
  }
}
