package models

import traits.{OutputLimits, MongoHelper, Model}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import java.util.Date
import reactivemongo.api.indexes.{IndexType, Index}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * User: Björn Reimer
 * Date: 6/25/13
 * Time: 9:45 PM
 */
case class User(
                 username: String,
                 email: String,
                 password: String,
                 name: Option[String],
                 phonenumber: Option[String],
                 contacts: Seq[Contact],
                 conversations: Seq[String],
                 created: Date,
                 lastUpdated: Date
                 )


object User extends Model[User] {

  // create index to unsure unique usernames and emails
  userCollection.indexesManager.ensure(Index(List("username" -> IndexType.Ascending), unique = true, sparse = true))
  userCollection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending), unique = true, sparse = true))

  implicit val collection = userCollection
  implicit val mongoFormat: Format[User] = createMongoFormat(Json.reads[User], Json.writes[User])


  def inputReads: Reads[User] = (
    (__ \ 'username).read[String] and
      (__ \ 'email).read[String](email) and
      (__ \ 'password).read[String](minLength[String](8) andKeep hashPassword) and
      (__ \ 'name).readNullable[String] and
      (__ \ 'phonenumber).readNullable[String] and
      Reads.pure[Seq[Contact]](Seq[Contact]()) and
      Reads.pure(Seq[String]()) and
      Reads.pure[Date](new Date) and
      Reads.pure[Date](new Date))(User.apply _)

  def outputWrites(implicit ol: OutputLimits = OutputLimits(0,0)): Writes[User] = Writes {
    user =>
      Json.obj("username" -> user.username) ++
        Json.obj("email" -> user.email) ++
        toJsonOrEmpty("phonenumber", user.phonenumber) ++
        toJsonOrEmpty("name", user.name) ++
  //      Contact.toSortedJsonArray("contacts", user.contacts) ++
  //      Json.obj("conversations" -> JsArray(user.conversations.map(JsString(_)).distinct)) ++
        addCreated(user.created) ++
        addLastUpdated(user.lastUpdated)
  }

  def find(username: String): Future[Option[User]] = {
    val query = Json.obj("username" -> username)
    collection.find(query).one[User]
  }


}
