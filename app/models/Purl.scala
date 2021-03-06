package models

import helper.IdHelper
import helper.MongoCollections._
import play.api.Logger
import play.api.libs.json._
import traits.Model

/**
 * User: Björn Reimer
 * Date: 9/1/13
 * Time: 5:23 PM
 */

case class Purl(id: MongoId,
                messageId: MongoId,
                identityId: MongoId) {

  def toJson: JsObject = Json.toJson(this)(Purl.outputWrites).as[JsObject]

}

object Purl extends Model[Purl] {

  implicit val col = purlCollection

  implicit val mongoFormat: Format[Purl] = createMongoFormat(Json.reads[Purl], Json.writes[Purl])

  def evolutions = Map()

  // Input/output format for the API
  def createReads = Json.reads[Purl]

  def outputWrites: Writes[Purl] = Writes {
    purl =>
      Json.obj("conversationId" -> purl.messageId.toJson) ++
        Json.obj("identityId" -> purl.identityId.toJson) ++
        Json.obj("id" -> purl.id.toJson)
  }

  def create(messageId: MongoId, identityId: MongoId): Purl = {
    val purl = IdHelper.generatePurl()
    Logger.debug("Purl generated: " + purl.toString)
    new Purl(purl,
      messageId,
      identityId)
  }

  override def createDefault(): Purl = {
    new Purl(IdHelper.generatePurl(), IdHelper.generateMessageId(), IdHelper.generateIdentityId())
  }
}