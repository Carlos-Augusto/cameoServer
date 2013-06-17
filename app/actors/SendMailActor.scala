package actors

import akka.actor.Actor
import play.api.{Play, Logger}
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.simpleemail.model._
import play.api.Play.current
import play.api.libs.json.{JsString, Json, JsObject}
import traits.{MongoHelper, JsonTransformer}
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import com.amazonaws.{AmazonServiceException, AmazonClientException}

/**
 * User: Björn Reimer
 * Date: 6/12/13
 * Time: 8:01 PM
 */
class SendMailActor extends Actor with JsonTransformer with MongoHelper {

  def receive = {
    case (recipient: JsObject, message: JsObject, user: JsObject) => {
      val from = "kolibri-test@jaymob.de"
      val to = (recipient \ "sendTo").asOpt[String].getOrElse("no email given")
      val subject = "[KolibriNet] Message from " + (user \ "name").asOpt[String].getOrElse("no name")
      val body = (message \ "messageBody").asOpt[String].getOrElse("empty Body")
      val messageId = (message \ "messageId").asOpt[String].getOrElse("")
      val recipientId = (recipient \ "recipientId").asOpt[String].getOrElse("")
      val conversationId = (message \ "conversationId").asOpt[String].getOrElse("")

      // add footer the mail
      val bodyWithFooter = body + ("\n\n\n\n----------------------------------\nAnswer and view the entire conversation on Kolibrinet: http://kl.vc/c/" + conversationId)

      Logger.info("SendMailActor: Sending email to " + to + " from " + from + " with subject \'" + subject + "\'")
      val credentials = new BasicAWSCredentials(Play.configuration.getString("aws.accessKey").getOrElse(""),
        Play.configuration.getString("aws.secretKey").getOrElse(""))
      val client = new AmazonSimpleEmailServiceClient(credentials)

      val sendEmailRequest = new SendEmailRequest()

      val dest = new Destination().withToAddresses(to)
      sendEmailRequest.setDestination(dest)
      sendEmailRequest.setSource(from)
      val awsBody = new Body().withText(new Content().withData(bodyWithFooter))
      val awsMessage = new Message().withBody(awsBody).withSubject(new Content().withData(subject))
      sendEmailRequest.setMessage(awsMessage)

      var status: String = ""
      try {
        val result = client.sendEmail(sendEmailRequest)
        status = "Mail send. Id: " + result.getMessageId
      } catch {
        case ce: AmazonClientException => status = "Error sending Mail, Could not connect to Amazon"; Logger.error("Error sending mail", ce)
        case se: AmazonServiceException => status = "Error sending Mail, Could not connect to Amazon"; Logger.error("Error sending mail", se)
      }

      val query = Json.obj("messages." + messageId -> Json.obj("$exists" -> true))
      val set = Json.obj("$set" -> Json.obj("messages." + messageId + ".recipients." + recipientId + ".status" ->
        JsString(status)))

      conversationCollection.update(query, set).map {
        lastError => if (lastError.inError) {
          Logger.error("Error updating recipient")
        }
      }

      Logger.info("SendMailActor: " +status)
    }
  }

}
