package models

import java.util.Date
import play.api.libs.json._
import play.api.libs.functional.syntax._
import helper.IdHelper
import traits.{MongoHelper, ModelHelper}

/**
 * User: Björn Reimer
 * Date: 6/25/13
 * Time: 5:53 PM
 */
case class Contact(
                    contactId: String,
                    name: String,
                    email: Option[String],
                    phonenumber: Option[String],
                    groups: Seq[String],
                    username: Option[String],
                    created: Date,
                    lastUpdated: Date
                  )

object Contact extends ModelHelper with MongoHelper {

  // Writing to/reading from MongoDB
  implicit val mongoFormat = createMongoFormat(Json.reads[Contact], Json.writes[Contact])

  // Input/output format for the API
  val inputReads: Reads[Contact] = (
    Reads.pure[String](IdHelper.generateContactId()) and
    ((__ \ 'name).read[String] or Reads.pure(IdHelper.generateContactId())) and
    (__ \ 'email).readNullable[String] and
    (__ \ 'phonenumber).readNullable[String] and
    ((__ \ 'groups).read[Seq[String]] or Reads.pure(Seq[String]())) and
    (__ \ 'username).readNullable[String] and
    Reads.pure[Date](new Date) and
    Reads.pure[Date](new Date)
    )(Contact.apply _)

  val outputWrites: Writes[Contact] = Writes {
    contact =>
      Json.obj("name" -> contact.name) ++
        toJsonOrEmpty(contact.email, "email") ++
        toJsonOrEmpty(contact.phonenumber, "phonenumber") ++
        toJsonOrEmpty(contact.username, "username") ++
        Json.obj("groups" -> contact.groups) ++
        Json.obj("contactId" -> contact.contactId) ++
        Json.obj("lastUpdated" -> defaultDateFormat.format(contact.lastUpdated))
  }

  val sortWith = {
    (c1: Contact, c2: Contact) => c1.name < c2.name
  }
}

