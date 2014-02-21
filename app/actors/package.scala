import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 * User: Björn Reimer
 * Date: 10/14/13
 * Time: 3:10 PM
 */
package object actors {
  lazy val notificationActor = Akka.system.actorOf(Props[NotificationActor], name = "Notification")
  lazy val sendMailActor = Akka.system.actorOf(Props[SendMailActor], name = "sendMail")
  lazy val sendSmsActor = Akka.system.actorOf(Props[SendSmsActor], name = "sendSMS")
  lazy val sendKolibriActor = Akka.system.actorOf(Props[SendKolibriActor], name = "sendKolibri")
  lazy val verifyActor = Akka.system.actorOf(Props[VerifyActor], name = "verify")
}
