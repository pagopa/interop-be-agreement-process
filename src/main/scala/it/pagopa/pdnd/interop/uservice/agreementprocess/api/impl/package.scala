package it.pagopa.pdnd.interop.uservice.agreementprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, deserializationError}

import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  type ManagementEService     = catalogmanagement.client.model.EService
  type ManagementOrganization = partymanagement.client.model.Organization
  type ManagementAgreement    = agreementmanagement.client.model.Agreement
  type ManagementAttributes    = catalogmanagement.client.model.Attributes

  implicit val uuidFormat: JsonFormat[UUID] =
    new JsonFormat[UUID] {
      override def write(obj: UUID): JsValue = JsString(obj.toString)

      override def read(json: JsValue): UUID = json match {
        case JsString(s) =>
          Try(UUID.fromString(s)) match {
            case Success(result) => result
            case Failure(exception) =>
              deserializationError(s"could not parse $s as UUID", exception)
          }
        case notAJsString =>
          deserializationError(s"expected a String but got a ${notAJsString.compactPrint}")
      }
    }

  implicit val localTimeFormat: JsonFormat[OffsetDateTime] = new JsonFormat[OffsetDateTime] {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val deserializationErrorMessage =
      s"Expected date time in ISO offset date time format ex. ${OffsetDateTime.now().format(formatter)}"

    override def write(obj: OffsetDateTime): JsValue = JsString(formatter.format(obj))

    override def read(json: JsValue): OffsetDateTime = {
      json match {
        case JsString(lTString) =>
          Try(OffsetDateTime.of(LocalDateTime.parse(lTString, formatter), ZoneOffset.UTC))
            .getOrElse(deserializationError(deserializationErrorMessage))
        case _ => deserializationError(deserializationErrorMessage)
      }
    }
  }

  def extractBearer(contexts: Seq[(String, String)]): Future[String] = Future.fromTry {
    contexts.toMap.get("bearer").toRight(new RuntimeException("Bearer token has not been passed")).toTry
  }

  implicit class OptionOps[A](val option: Option[A]) extends AnyVal {
    def toFuture(error: Throwable): Future[A] = option.map(Future.successful).getOrElse(Future.failed[A](error))
  }
}
