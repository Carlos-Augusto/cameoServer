package actors

import akka.actor.{ Props, Actor }
import constants.Verification._
import models.{ SmsMessage, MailMessage, VerificationSecret, Identity }
import play.api.Play
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import play.api.Play.current
import play.api.libs.concurrent.Akka

/**
 * User: Björn Reimer
 * Date: 2/3/14
 * Time: 5:43 PM
 */
class VerifyActor extends Actor {

  def receive = {

    case (VERIFY_TYPE_MAIL, identity: Identity) => {

      val email = identity.email.map {
        _.toString
      }.getOrElse("")

      val secret = VerificationSecret.create(identity.id, email, VERIFY_TYPE_MAIL)
      VerificationSecret.col.insert(secret)

      val link = Play.configuration.getString("shortUrl.address").get + "/v/" + secret.id

      val body = "Click the following link to verify this mail address (" + email + ")\n\n" + link

      val from = Play.configuration.getString("verification.mail.from").get
      val subject = "[Cameo] Mail verification"

      val mail = new MailMessage(from, email, body, subject)

      lazy val sendMailActor = Akka.system.actorOf(Props[SendMailActor])
      sendMailActor ! (mail, 0)
    }

    case (VERIFY_TYPE_PHONENUMBER, identity: Identity) => {

      val number = identity.phoneNumber.map {
        _.toString
      }.getOrElse("")

      val secret = VerificationSecret.create(identity.id, number, VERIFY_TYPE_PHONENUMBER)
      VerificationSecret.col.insert(secret)

      val link = Play.configuration.getString("shortUrl.address").get + "/v/" + secret.id

      val body = "Click the following link to verify this phonenumber (" + number + ")\n\n" + link

      val from = Play.configuration.getString("verification.sms.from").get

      val sms = new SmsMessage(from, number, body)

      lazy val sendSmsActor = Akka.system.actorOf(Props[SendSmsActor])
      sendSmsActor ! (sms, 0)
    }
  }

}
