package services

import models.{TwoFactorSmsKey, SmsMessage, VerifiedString, Identity}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.concurrent.Akka
import actors.SendMailActor
import play.api.Play.current
import ExecutionContext.Implicits.global
import global._
import play.api.{Play, Logger}

/**
 * User: Björn Reimer
 * Date: 3/13/14
 * Time: 4:28 PM
 */
object TwoFactorAuth {

  def sendNewKey(identity: Identity): Option[String] = {
    // check if the user has a phonenumber TODO: require that the phonenumber is verified
    identity.phoneNumber match {
      case Some(VerifiedString(_, number, _)) => {
        val key = TwoFactorSmsKey.create(identity.id)
        val sms = new SmsMessage("Cameo Two Factor Auth", number, key.toString)
        val sendSmsActor = Akka.system.actorOf(actors.SendSmsActorProps)
        sendSmsActor ! (sms, 0)
        None
      }
      case _ => Some("identity has no phonenumber")
    }
  }

  def verifyKey(key: String, identity: Identity): Future[Boolean] = {

    TwoFactorSmsKey.find(key).map {
      case None => false
      case Some(twoFactorSmsKey) =>
        // compare identities
        twoFactorSmsKey.identityId.equals(identity.id) match {
          case false => false
          case true =>
            // delete key
            twoFactorSmsKey.delete
            true
        }
    }
  }
}
