package actors

import javax.mail.internet.MimeUtility

import akka.actor.Actor
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpleemail.model._
import com.amazonaws.services.simpleemail.{ AmazonSimpleEmailServiceClient, model }
import com.amazonaws.{ AmazonClientException, AmazonServiceException }
import play.api.Play.current
import play.api.libs.json.Json
import play.api.{ Logger, Play }

/**
 * User: Björn Reimer
 * Date: 6/12/13
 * Time: 8:01 PM
 */
case class Mail(fromName: String, fromMail: String, to: String, body: String, subject: String)
object Mail { implicit val format = Json.format[Mail] }

class SendMailActor extends Actor {

  def receive = {

    case mail: Mail =>
      Logger.debug("SendMailActor: Sending email to " + mail.to + " from " + mail.fromName + " with subject \'" + mail.subject + "\'")

      // check if there are there are credentials in the config
      val accessKey = Play.configuration.getString("aws.accessKey")
      val secretKey = Play.configuration.getString("aws.secretKey")

      accessKey.isEmpty || secretKey.isEmpty match {
        case true =>
          Logger.warn("No AWS credentials")
        case false =>
          val credentials = new BasicAWSCredentials(accessKey.get, secretKey.get)
          val client = new AmazonSimpleEmailServiceClient(credentials)
          client.setEndpoint("email.eu-west-1.amazonaws.com")
          val sendEmailRequest = new SendEmailRequest()
          val dest = new Destination().withToAddresses(mail.to)
          sendEmailRequest.setDestination(dest)
          val from: String = MimeUtility.encodeText(mail.fromName, "utf-8", null) + "<" + mail.fromMail + ">"
          sendEmailRequest.setSource(from)
          val awsBody = new Body().withText(new Content().withData(mail.body))
          val awsMessage = new model.Message().withBody(awsBody).withSubject(new Content().withData(mail.subject))
          sendEmailRequest.setMessage(awsMessage)

          try {
            val result = client.sendEmail(sendEmailRequest)
            Logger.info("Mail send. Id: " + result.getMessageId)
          } catch {
            case ce: AmazonClientException  => Logger.error("ACE", ce)
            case se: AmazonServiceException => Logger.error("ACE", se)
          }
      }
  }
}

