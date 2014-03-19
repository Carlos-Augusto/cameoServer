package models.cockpit.attributes

import play.api.libs.json._
import traits.CockpitAttribute
import play.api.Logger
import scala.Some
import play.api.libs.json.JsObject
import scala.Some

/**
 * User: Björn Reimer
 * Date: 3/19/14
 * Time: 11:51 AM
 */
case class CockpitAttributeOptionList[A](name: String,
                                         displayName: String,
                                         isEditable: Boolean = false,
                                         showInList: Boolean = false)(implicit val format: Format[A]) extends CockpitAttribute {

  def getTypeName = "optionList"
  def getShowInList = showInList
  def getIsEditable = isEditable
  def getName = name
  def getDisplayName = displayName

  def getData(js: JsObject): Option[JsValue] = {
    (js \ name).asOpt[JsValue] match {
      case None => None
      case Some(attributeJs) =>
        attributeJs.asOpt[A] match {
          case None =>
            Logger.error("AttributeDoes not match specified type: " + js)
            None
          case Some(obj) => Some(Json.toJson(obj))

        }
    }
  }

  def getListString(js: JsObject): Option[String] = {
    showInList match {
      case false => None
      case true  => getData(js).map(_.toString())
    }
  }

  def getTransformerFromData(data: JsValue): Option[Reads[JsObject]] = {

    data.asOpt[A] match {
      case None =>
        Logger.error("Cannot be converted back to type: " + data)
        None
      case Some(obj) =>
        Some(__.json.update((__ \ name).json.put(Json.toJson(obj))))
    }
  }
}

