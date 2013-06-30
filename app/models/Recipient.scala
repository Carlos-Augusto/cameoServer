package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import helper.IdHelper
import traits.{OutputLimits, Model, MongoHelper}

/**
 * User: Björn Reimer
 * Date: 6/26/13
 * Time: 2:35 PM
 */
case class Recipient(
                      recipientId: String,
                      name: String,
                      messageType: String,
                      sendTo: String,
                      sendStatus: Option[String] ,
                      testRun: Option[Boolean]
                      )

object Recipient extends Model[Recipient] {

  implicit val collection = userCollection
  implicit val mongoFormat: Format[Recipient] = createMongoFormat(Json.reads[Recipient], Json.writes[Recipient])

  def inputReads = (
    Reads.pure[String](IdHelper.generateRecipientId()) and
      (__ \ 'name).read[String] and
      (__ \ 'messageType).read[String] and
      (__ \ 'sendTo).read[String] and
      Reads.pure(None) and
      (__ \ 'test).readNullable[Boolean]
    )(Recipient.apply _)

  def outputWrites(implicit ol: OutputLimits = OutputLimits(0,0)) = Writes[Recipient] {
    r =>
      Json.obj("recipientId" -> r.recipientId) ++
      Json.obj("name" -> r.name) ++
      Json.obj("messageType" -> r.messageType) ++
      Json.obj("sendTo" -> r.sendTo) ++
      toJsonOrEmpty("sendStatus", r.sendStatus)
  }

  Json.writes[Recipient]

  override val sortWith = {
    (r1: Recipient, r2: Recipient) => r1.name < r2.name
  }
}
