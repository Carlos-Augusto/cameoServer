package models

import java.util.Date
import traits.{ Model }
import play.api.libs.json._
import helper.IdHelper
import play.api.libs.functional.syntax._
import scala.concurrent.{ ExecutionContext, Future }
import ExecutionContext.Implicits.global
import helper.MongoHelper._
import play.modules.reactivemongo.json.collection.JSONCollection

/**
 * User: Björn Reimer
 * Date: 6/26/13
 * Time: 2:36 PM
 */
case class Message(id: MongoId,
                   messageBody: String,
                   fromIdentityId: MongoId,
                   messageStatus: Seq[MessageStatus],
                   assets: Seq[Asset],
                   created: Date) {

  def toJson: JsObject = Json.toJson(this)(Message.outputWrites).as[JsObject]

  def updateStatus(messageStatus: Seq[MessageStatus]) = {

    val update = Json.obj("$set" -> Json.obj("messages.$.messageStatus" -> messageStatus))
    Conversation.col.update(Message.messageQuery(this.id), update)
  }

}

object Message extends Model[Message] {

  def col: JSONCollection = conversationCollection

  implicit val mongoFormat: Format[Message] = createMongoFormat(Json.reads[Message], Json.writes[Message])

  def createReads(fromIdentityId: MongoId) = (
    Reads.pure[MongoId](IdHelper.generateMessageId()) and
    (__ \ 'messageBody).read[String] and
    Reads.pure[MongoId](fromIdentityId) and
    Reads.pure[Seq[MessageStatus]](Seq()) and
    Reads.pure[Seq[Asset]](Seq()) and
    Reads.pure[Date](new Date))(Message.apply _)

  def outputWrites = Writes[Message] {
    m =>
      Json.obj("id" -> m.id.toJson) ++
        Json.obj("messageBody" -> m.messageBody) ++
        Json.obj("fromIdentity" -> m.fromIdentityId.toJson) ++
        Json.obj("messageStatus" -> m.messageStatus.map(_.toJson)) ++
        addCreated(m.created)
  }

  def messageQuery(id: MongoId): JsObject = Json.obj("messages" -> Json.obj("$elemMatch" -> Json.obj("_id" -> id)))

  override def find(id: MongoId): Future[Option[Message]] = {
    val projection = Json.obj("messages" -> Json.obj("$elemMatch" -> Json.obj("_id" -> id)))

    Conversation.col.find(messageQuery(id), projection).one[JsValue].map {
      case None     => None
      case Some(js) => Some((js \ "messages")(0).as[Message])
    }

  }

  // gets the position of a message in a conversation
  //  def getMessagePosition(conversationId: String, messageId: String): Future[Int] ={
  //
  //    val query = Json.obj("conversationId" -> conversationId)
  //
  //    conversationCollection.find(query).one[Conversation].map{
  //      case None => -1
  //      case Some(c) => c.messages.indexWhere(m => {m.messageId.equals(messageId)})
  //    }
  //
  //  }
}

