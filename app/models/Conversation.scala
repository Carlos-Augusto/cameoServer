package models

import java.util.Date

import constants.KeyTransmission
import controllers.PublicKeyController.AePassphrase
import helper.JsonHelper._
import helper.MongoCollections._
import helper.ResultHelper._
import helper.{JsonHelper, IdHelper, MongoCollections}
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.Result
import play.modules.reactivemongo.json.BSONFormats._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.bson.{BSONInteger, BSONNull, BSONString}
import reactivemongo.core.commands._
import traits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * User: Björn Reimer
 * Date: 6/26/13
 * Time: 1:29 PM
 */

case class Conversation(id: MongoId,
                        subject: Option[String],
                        recipients: Seq[Recipient],
                        inactiveRecipients: Seq[Recipient],
                        conversationSignatures: Option[Seq[Signature]],
                        messages: Seq[Message],
                        aePassphraseList: Seq[EncryptedPassphrase],
                        sePassphrase: Option[String],
                        passCaptcha: Option[MongoId],
                        numberOfMessages: Int,
                        created: Date,
                        lastUpdated: Date,
                        keyTransmission: Option[String],
                        docVersion: Int) {

  def toJson(identityId: MongoId, settings: Option[AccountUserSettings], keyIds: Option[Seq[String]] = None): JsObject =
    Json.toJson(this)(Conversation.outputWrites).as[JsObject] ++
      Json.obj("unreadMessages" -> this.getNumberOfUnreadMessages(identityId, settings)) ++
      maybeEmptyJson("aePassphraseList", keyIds.map(this.getPassphraseList))

  def getPassphraseList(keyIds: Seq[String]): Seq[JsObject] = {
    aePassphraseList.filter(passphrase => keyIds.contains(passphrase.keyId)).map(_.toJson)
  }

  def getNumberOfUnreadMessages(identityId: MongoId, settings: Option[AccountUserSettings]): Int = {
    (settings.map(_.enableUnreadMessages), this.recipients.find(_.identityId.equals(identityId))) match {
      case (_, None) =>
        Logger.error("Trying to get number of messages of recipient who is not member of the conversation"); -1
      case (None, _) => -1
      case (Some(false), _) => -1
      case (Some(true), Some(recipient)) => this.numberOfMessages - recipient.messagesRead.getOrElse(0)
    }
  }

  def toMessageJson: JsObject = Json.toJson(this)(Conversation.messageWrites).as[JsObject]

  def toSummaryJson(identityId: MongoId, settings: Option[AccountUserSettings], keyIds: Seq[String]): JsObject = {
    Json.toJson(this)(Conversation.summaryWrites).as[JsObject] ++
      Json.obj("aePassphraseList" -> getPassphraseList(keyIds)) ++
      Json.obj("unreadMessages" -> getNumberOfUnreadMessages(identityId, settings)) ++
      Json.obj("conversationSignatures" -> getConversationSignatures)
  }

  def query = Json.obj("_id" -> this.id)

  def setLastUpdated(js: JsObject): JsObject = {
    // check if there already is a $set block
    val set: JsValue = (js \ "$set").asOpt[JsValue] match {
      case None => Json.obj("lastUpdated" -> new Date)
      case Some(obj: JsObject) => obj ++ Json.obj("lastUpdated" -> new Date)
      case Some(ar: JsArray) => ar.append(Json.obj("lastUpdated" -> new Date))
      case Some(other) => Logger.error("SetLastUpdated: Unable to process: " + js); other
    }
    js ++ Json.obj("$set" -> set)
  }

  def getMessage(messageId: MongoId): Option[Message] = {
    this.messages.find(_.id.equals(messageId))
  }

  def getMessageCount: Future[Int] = {
    val pipeline: Seq[PipelineOperator] = Seq(
      Match(toBson(query).get),
      Unwind("messages"),
      Group(BSONNull)(("count", SumValue(1))))

    val command = Aggregate(Conversation.col.name, pipeline)

    MongoCollections.mongoDB.command(command).map {
      res =>
        res.headOption match {
          case None => 0
          case Some(bson) =>
            val js = Json.toJson(bson)
            (js \ "count").as[Int]
        }
    }
  }

  def addRecipients(recipients: Seq[Recipient]): Future[Boolean] = {
    Recipient.appendUnique(this.id, recipients).map(_.updatedExisting)
  }

  def deleteRecipient(identityId: MongoId): Future[Boolean] = {
    Recipient.delete(this.id, identityId).map(_.updatedExisting)
  }

  def markRecipientInactive(identityId: MongoId): Future[Option[Recipient]] = {
    this.recipients.find(_.identityId.equals(identityId)) match {
      case None => Future(None)
      case Some(recipient) =>
        // delete from recipient list and add to inactive list
        for {
          delete <- deleteRecipient(identityId)
          add <- {
            val update = Json.obj("$push" -> Json.obj("inactiveRecipients" -> Json.toJson(recipient)))
            Conversation.update(this.id, update)
          }
        } yield {
          if (delete && add) {
            Some(recipient)
          } else {
            None
          }
        }
    }
  }

  def hasMember(identityId: MongoId): Boolean = {
    val res = this.recipients.exists(_.identityId.equals(identityId))
    res
  }

  def hasMemberResult(identityId: MongoId)(action: => Result): Result = {
    this.hasMember(identityId) match {
      case true => action
      case false => resUnauthorized("identity is not a member of the conversation")
    }
  }

  def hasMemberFutureResult(identityId: MongoId)(action: => Future[Result]): Future[Result] = {
    this.hasMember(identityId) match {
      case true => action
      case false => Future(resUnauthorized("identity is not a member of the conversation"))
    }
  }

  def addMessage(message: Message, fromIdentityId: MongoId): Future[Boolean] = {
    val query =
      Json.obj(
        "_id" -> this.id,
        "recipients.identityId" -> fromIdentityId
      )

    val set =
      Json.obj(
        "$push" -> Json.obj("messages" ->
          Json.obj(
            "$each" -> Seq(message),
            "$position" -> 0
          )),
        "$inc" -> Json.obj("numberOfMessages" -> 1),
        "$inc" -> Json.obj("recipients.$.messagesRead" -> 1)
      )
    Conversation.col.update(query, setLastUpdated(set)).map(_.updatedExisting)
  }

  def addAePassphrases(aePassphrases: Seq[EncryptedPassphrase]): Future[Boolean] = {
    Conversation.addAePassphrases(aePassphrases, this.id)
  }

  def deleteAePassphrases(keyIds: Seq[String]): Future[Boolean] = {
    val query = Json.obj("$or" -> keyIds.map(keyId => Json.obj("keyId" -> keyId)))
    EncryptedPassphrase.deleteAll(this.id, query).map(_.updatedExisting)
  }

  def getMissingPassphrases: Future[Seq[String]] = {

    // get keyIds of all recipients. Todo: this takes quite a lot of db lookups, reduce!
    val futureKeys: Seq[Future[Seq[String]]] = this.recipients.map {
      recipient =>
        Identity.find(recipient.identityId).map {
          case None => Seq()
          case Some(identity) =>
            val filtered = identity.publicKeys.filterNot(pubKey => this.aePassphraseList.exists(_.keyId.equals(pubKey.id.id)))
            filtered.map(_.id.toString)
        }
    }
    Future.sequence(futureKeys).map(_.flatten)
  }

  def getConversationSignatures: Seq[Signature] = {
    conversationSignatures match {
      case Some(sigs) => sigs
      case None => Seq.empty
    }
  }

  def markMessageRead(identityId: MongoId, stillUnread: Int): Future[Boolean] = {
    val query = Json.obj("_id" -> this.id, "recipients.identityId" -> identityId)
    val set = Json.obj("$set" -> Json.obj("recipients.$.messagesRead" -> (this.numberOfMessages - stillUnread)))
    Conversation.col.update(query, set).map(_.updatedExisting)
  }
}

object Conversation extends Model[Conversation] {

  def col: JSONCollection = conversationCollection

  implicit val mongoFormat: Format[Conversation] = createMongoFormat(Json.reads[Conversation], Json.writes[Conversation])

  def createReads: Reads[Conversation] = (
    (__ \ "subject").readNullable[String] and
      Reads.pure(Seq()) and
      (__ \ "conversationSignatures").readNullable[Seq[Signature]] and
      (__ \ "passCaptcha").readNullable[String] and
      (__ \ "aePassphraseList").readNullable(Reads.seq(EncryptedPassphrase.createReads)) and
      (__ \ "sePassphrase").readNullable[String] and
      (__ \ "keyTransmission").readNullable[String]
    )(Conversation.create _)

  def outputWrites = Writes[Conversation] {
    c =>
      Json.obj("id" -> c.id.toJson) ++
        Json.obj("recipients" -> c.recipients.map(_.toJson)) ++
        Json.obj("inactiveRecipients" -> c.inactiveRecipients.map(_.toJson)) ++
        Json.obj("messages" -> c.messages.map(_.toJson)) ++
        Json.obj("aePassphraseList" -> c.aePassphraseList.map(_.toJson)) ++
        Json.obj("numberOfMessages" -> c.numberOfMessages) ++
        maybeEmptyJson("sePassphrase", c.sePassphrase) ++
        maybeEmptyJson("subject", c.subject) ++
        maybeEmptyJson("keyTransmission", c.keyTransmission) ++
        maybeEmptyJson("passCaptcha", c.passCaptcha.map(_.toString)) ++
        maybeEmptyJson("conversationSignatures", c.conversationSignatures) ++
        addCreated(c.created) ++
        addLastUpdated(c.lastUpdated)
  }

  val summaryWrites = Writes[Conversation] {
    c =>
      Json.obj("id" -> c.id.toJson) ++
        addLastUpdated(c.lastUpdated) ++
        Json.obj("recipients" -> c.recipients.map(_.toJson)) ++
        Json.obj("inactiveRecipients" -> c.inactiveRecipients.map(_.toJson)) ++
        Json.obj("numberOfMessages" -> c.numberOfMessages) ++
        maybeEmptyJson("subject", c.subject) ++
        maybeEmptyJson("keyTransmission", c.keyTransmission) ++
        Json.obj("messages" -> c.messages.map(_.toJson)) ++
        maybeEmptyJson("sePassphrase", c.sePassphrase) ++
        maybeEmptyJson("passCaptcha", c.passCaptcha.map(_.toString))
  }

  val messageWrites = Writes[Conversation] {
    c =>
      Json.obj("id" -> c.id.toJson) ++
        Json.obj("numberOfMessages" -> c.numberOfMessages) ++
        Json.obj("messages" -> c.messages.map(_.toJson))
  }

  def find(id: String, limit: Int, offset: Int): Future[Option[Conversation]] = {
    find(MongoId(id), limit, offset)
  }

  def findWithTimeLimit(id: String, timeLimit: Long): Future[Option[Conversation]] = {
    // we need to get the whole conversation and apply the limit, since we cant do this easily in mongodb.
    // todo: implement with aggregation framework
    find(MongoId(id)).map {
      _.map {
        conversation => conversation.copy(messages = conversation.messages.filter(_.created.after(new Date(timeLimit))))
      }
    }
  }

  def find(id: MongoId, limit: Int, offset: Int): Future[Option[Conversation]] = {
    val query = Json.obj("_id" -> id)
    val projection = limitArray("messages", limit, offset)
    col.find(query, projection).one[Conversation]
  }

  def findByMessageId(id: MongoId, limit: Int, offset: Int): Future[Option[Conversation]] = {
    val query = Json.obj("messages._id" -> id)
    col.find(query, limitArray("messages", limit, offset)).one[Conversation]
  }

  def findByIdentityId(id: MongoId): Future[Seq[Conversation]] = {
    val query = Json.obj(
      "recipients.identityId" -> id
    )
    col.find(query, limitArray("messages", 1, 0)).cursor[Conversation].collect[Seq]()
  }

  def search(searchingIdentity: MongoId, subject: Option[String], recipients: Seq[MongoId]): Future[Seq[Conversation]] = {
    val query = Json.obj(
      "recipients.identityId" -> searchingIdentity,
      "$or" -> Seq(
        JsonHelper.maybeEmptyJson("subject", subject.map(s => Json.obj("$regex" -> s, "$options" -> "i"))),
        Json.obj("recipients.identityId" -> Json.obj("$in" -> recipients))
      )
    )

    findAll(query)
  }

  def getAePassphrases(identityId: MongoId, oldKeyId: MongoId, newKeyId: MongoId, limit: Option[Int]): Future[Seq[AePassphrase]] = {

    val pipeline: Seq[PipelineOperator] =
      Seq(
        Match(toBson(Json.obj("recipients.identityId" -> identityId)).get),
        Match(toBson(Json.obj("aePassphraseList.keyId" -> oldKeyId.id)).get),
        Match(toBson(Json.obj("aePassphraseList.keyId" -> Json.obj("$nin" -> Seq(newKeyId.id)))).get),
        Project(("aePassphraseList", BSONInteger(1))),
        Unwind("aePassphraseList"),
        Match(toBson(Json.obj("aePassphraseList.keyId" -> oldKeyId.id)).get),
        Project(("aePassphrase", BSONString("$aePassphraseList.value")), ("conversationId", BSONString("$_id.mongoId")))
      ) ++ {
        limit match {
          case None => Seq()
          case Some(int) => Seq(Limit(int))
        }
      }

    val aggregationCommand = Aggregate(col.name, pipeline)

    mongoDB.command(aggregationCommand).map {
      _.map(Json.toJson(_).as[AePassphrase])
    }
  }

  def addAePassphrases(aePassphrases: Seq[EncryptedPassphrase], conversationId: MongoId): Future[Boolean] = {
    val futureResults = aePassphrases.map { aePassphrase =>
      EncryptedPassphrase.appendOrUpdate(conversationId, aePassphrase)
    }
    Future.sequence(futureResults).map(!_.contains(false))
  }

  def create(subject: Option[String] = None,
             recipients: Seq[Recipient] = Seq(),
             conversationSignatures: Option[Seq[Signature]] = None,
             passCaptcha: Option[String] = None,
             aePassphraseList: Option[Seq[EncryptedPassphrase]] = None,
             sePassphrase: Option[String] = None,
             keyTransmission: Option[String] = Some(KeyTransmission.KEY_TRANSMISSION_NONE)): Conversation = {
    val id = IdHelper.generateConversationId()
    new Conversation(id, subject, recipients, Seq(), conversationSignatures, Seq(), aePassphraseList.getOrElse(Seq()), sePassphrase, passCaptcha.map(new MongoId(_)), 0, new Date, new Date, keyTransmission, docVersion)
  }

  def evolutions = Map(
    0 -> ConversationEvolutions.addInactiveRecipients
  )

  def createDefault(): Conversation = create()
}

object ConversationModelUpdate extends ModelUpdate {
  def values = Seq(
    StringUpdateValue("subject", externalEdit = true),
    MongoIdUpdateValue("passCaptcha", externalEdit = true),
    StringUpdateValue("sePassphrase", externalEdit = true),
    StringUpdateValue("keyTransmission", externalEdit = true)
  )
}

object ConversationEvolutions {

  val addInactiveRecipients: Reads[JsObject] = Reads {
    js => {
      val addEmptyList: Reads[JsObject] = __.json.update((__ \ 'inactiveRecipients).json.put(JsArray()))
      val addVersion = __.json.update((__ \ 'docVersion).json.put(JsNumber(1)))
      js.transform(addEmptyList andThen addVersion)
    }
  }
}