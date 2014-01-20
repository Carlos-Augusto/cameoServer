package models

import reactivemongo.bson.BSONObjectID

import play.api.libs.json._
import play.api.libs.json.Reads._
import helper.IdHelper

/**
 * User: Björn Reimer
 * Date: 1/16/14
 * Time: 4:28 PM
 */

case class MongoId(id: String) {

  override def toString = id

  def toJson: JsString = {
    JsString(id)
  }
}

object MongoId {

  def create(): MongoId = {
    new MongoId(IdHelper.generateMongoId())
  }

  implicit def mongoReads: Reads[MongoId] =
    (__ \ 'mongoId).read[String].map {
      l => MongoId(l)
    }

  implicit def mongoWrites: Writes[MongoId] = Writes {
    id => Json.obj("mongoId" -> id.id)
  }

}
