package traits

import play.api.libs.json._
import play.api.libs.json.Reads._
import java.text.SimpleDateFormat
import org.mindrot.jbcrypt.BCrypt
import java.util.{TimeZone, Date}
import play.modules.reactivemongo.json.collection.JSONCollection
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

/**
 * User: Björn Reimer
 * Date: 6/25/13
 * Time: 6:46 PM
 */
//case class OutputLimits(offset: Int, limit: Int)

trait Model[A] extends MongoHelper {

  implicit val mongoFormat: Format[A]

  //def createReads: Reads[A]
  //def outputWrites(implicit ol: OutputLimits): Writes[A]

  /**
   * Helper
   */
//  def toJson(model: A)(implicit ol: OutputLimits = OutputLimits(0, 0)): JsValue = {
//    Json.toJson[A](model)(outputWrites)
//  }

//  def toJsonCustomWrites(model: A, writes: Writes[A])(implicit ol: OutputLimits = OutputLimits(0, 0)): JsValue = {
//    Json.toJson[A](model)(writes)
//  }

  def toJsonOrEmpty(key: String, value: Option[String]): JsObject = {
    value match {
      case Some(s) => Json.obj(key -> JsString(s))
      case None => Json.obj()
    }
  }

  def maybeEmpty(key: String, value: Option[JsObject]): JsObject = {
    value match {
      case Some(s) => Json.obj(key -> s)
      case None => Json.obj()
    }
  }

  def toJsonArrayOrEmpty(key: String, value: Option[Seq[String]]): JsObject = {
    value match {
      case Some(s) => Json.obj(key -> JsArray(s.map(JsString)))
      case None => Json.obj()
    }
  }

    // TODO: put this somewhere more sensible
  val hashPassword: Reads[String] = Reads[String] {
    js => js.asOpt[String] match {
      case None => JsError("No password")
      case Some(pass) => JsSuccess(BCrypt.hashpw(pass, BCrypt.gensalt()))
    }
  }

}
