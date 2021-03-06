package models.cockpit

import play.api.libs.json.{ JsObject, Json, Writes }

/**
 * User: Björn Reimer
 * Date: 3/11/14
 * Time: 11:59 AM
 */
case class CockpitList(titles: Seq[String],
                       elements: Seq[CockpitListElement],
                       availableFilters: Seq[CockpitListFilter]) {
  def toJson: JsObject = Json.toJson(this).as[JsObject]
}

object CockpitList {
  implicit val writes: Writes[CockpitList] = Json.writes[CockpitList]
}

case class CockpitListElement(id: String,
                              attributes: Map[String, Option[String]]) {
  def toJson: JsObject = Json.toJson(this).as[JsObject]
}
object CockpitListElement {
  implicit val writes: Writes[CockpitListElement] = Json.writes[CockpitListElement]
}

case class CockpitListFilter(filterName: String, filterFunction: String => JsObject)
object CockpitListFilter {
  implicit val writes: Writes[CockpitListFilter] = Writes {
    clf => Json.obj("filterName" -> clf.filterName)
  }
}

