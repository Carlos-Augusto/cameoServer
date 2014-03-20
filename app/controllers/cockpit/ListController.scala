package controllers.cockpit

import play.api.mvc.Action
import play.api.libs.json.{ Reads, Json }
import scala.concurrent.{ ExecutionContext, Future }
import models.Identity
import traits.{ CockpitEditableDefinition, ExtendedController }
import ExecutionContext.Implicits.global
import helper.TwoFactorAuthAction
import helper.ResultHelper._

/**
 * User: Björn Reimer
 * Date: 3/11/14
 * Time: 4:33 PM
 */
object ListController extends ExtendedController {

  def allEditables = Seq(
    new CockpitEditableDefinition("identity", Identity.getCockpitList, Identity.delete, Identity.newCockpitListElement, Identity.getAttributes, Identity.updateElement)
  )

  def getEditable(name: String): Option[CockpitEditableDefinition] = {
    allEditables.find(definition => definition.name.equals(name))
  }

  case class SelectedFilters(name: String, term: String)

  object SelectedFilters {
    implicit val reads: Reads[SelectedFilters] = Json.reads[SelectedFilters]
  }

  case class ListOptions(limit: Int,
                         offset: Int,
                         filter: Seq[SelectedFilters])
  object ListOptions {
    implicit val reads: Reads[ListOptions] = Json.reads[ListOptions]
  }

  def getAllLists() = TwoFactorAuthAction {
    val allNames: Seq[String] = allEditables.map { _.name }
    resOK(Json.obj("lists" -> Json.toJson(allNames)))
  }

  def list(elementName: String) = Action.async(parse.tolerantJson) {
    request =>
      validateFuture(request.body, ListOptions.reads) {
        listOptions =>
          {
            getEditable(elementName) match {
              case None => Future(resNotFound("elementName"))
              case Some(definition) => definition.getList(listOptions).map { list =>
                resOK(list.toJson)
              }
            }
          }
      }
  }

  def delete(elementName: String, id: String) = TwoFactorAuthAction.async {
    getEditable(elementName) match {
      case None => Future(resNotFound("elementName"))
      case Some(obj) => obj.delete(id).map {
        _.ok match {
          case false => resServerError("could not delete")
          case true  => resOK("deleted")
        }
      }
    }
  }

  def create(elementName: String) = TwoFactorAuthAction {
    getEditable(elementName) match {
      case None      => resNotFound("elementName")
      case Some(obj) => resOK(obj.create.toJson)
    }
  }
}

