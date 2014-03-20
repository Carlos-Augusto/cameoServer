package controllers.cockpit

import play.api.mvc.Action
import play.api.mvc.BodyParsers.parse
import scala.concurrent.{ ExecutionContext, Future }
import helper.ResultHelper._
import scala.Some
import ExecutionContext.Implicits.global
import play.api.libs.json.JsObject
import models.cockpit.CockpitEdit

object EditController {

  def edit(elementName: String, id: String) = Action.async {
    ListController.getEditable(elementName) match {
      case None => Future(resNotFound("entity with name: " + elementName))
      case Some(definition) => definition.getAttributes(id).map {
        case None => resNotFound(elementName + " object with id: " + id)
        case Some(attributes) =>
          val cockpitEdit = new CockpitEdit(id, attributes)
          resOK(cockpitEdit.toJson)
      }
    }
  }

  def modify(elementName: String, id: String) = Action.async(parse.tolerantJson) {
    request =>
      // todo: validate body
      ListController.getEditable(elementName) match {
        case None => Future(resNotFound("entity with name: " + elementName))
        case Some(definition) => definition.update(id, request.body.as[JsObject]).map {
          case None        => resBadRequest("invalid element id or update values")
          case Some(false) => resServerError("error saving update")
          case Some(true)  => resOK()
        }
      }
  }
}