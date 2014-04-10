package models

import play.api.libs.json._
import traits.Model
import play.api.libs.json.Reads._
import helper.{ MongoCollections, IdHelper }
import play.api.libs.functional.syntax._
import helper.JsonHelper._
import scala.concurrent.{ ExecutionContext, Future }
import reactivemongo.core.commands.LastError
import ExecutionContext.Implicits.global

/**
 * User: Björn Reimer
 * Date: 2/27/14
 * Time: 11:19 AM
 */
case class PublicKey(id: MongoId,
                     name: Option[String],
                     key: String,
                     keySize: Int,
                     docVersion: Int) {

  def toJson: JsObject = Json.toJson(this)(PublicKey.outputWrites).as[JsObject]

}

object PublicKey extends Model[PublicKey] {

  val col = MongoCollections.identityCollection

  implicit val mongoFormat: Format[PublicKey] = createMongoFormat(Json.reads[PublicKey], Json.writes[PublicKey])

  def createReads: Reads[PublicKey] = (
    Reads.pure[MongoId](IdHelper.generatePublicKeyId) and
    (__ \ 'name).readNullable[String] and
    (__ \ 'key).read[String] and
    (__ \ 'keySize).read[Int] and
    Reads.pure[Int](docVersion)
  )(PublicKey.apply _)

  def outputWrites: Writes[PublicKey] = Writes {
    pk =>
      Json.obj("id" -> pk.id.toJson) ++
        maybeEmptyString("name", pk.name) ++
        Json.obj("key" -> pk.key) ++
        Json.obj("keySize" -> pk.keySize)
  }

  def evolutions = Map(0 -> PublicKeyEvolutions.addKeySize)

  def docVersion = 1

  override def save(js: JsObject): Future[LastError] = {
    val id: MongoId = (js \ "_id").as[MongoId]
    val query = arrayQuery("publicKeys", id)
    val set = Json.obj("$set" -> js)
    col.update(query, set)
  }

  override def createDefault(): PublicKey = {
    new PublicKey(IdHelper.generatePublicKeyId, None, "", 0, docVersion)
  }
}

case class PublicKeyUpdate(name: Option[String], key: Option[String], keySize: Option[Int])

object PublicKeyUpdate {
  implicit val format: Format[PublicKeyUpdate] = Json.format[PublicKeyUpdate]
}

object PublicKeyEvolutions {

  val addKeySize: Reads[JsObject] = Reads {
    js =>
      {
        val keySize: Reads[JsObject] = __.json.update((__ \ 'keySize).json.put(JsNumber(0)))
        val addVersion = __.json.update((__ \ 'docVersion).json.put(JsNumber(1)))
        js.transform(keySize andThen addVersion)
      }
  }

}
