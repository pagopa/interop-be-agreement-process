package it.pagopa.interop.agreementprocess.events

import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import java.util.UUID

case class ArchiveEvent(agreementId: UUID, eserviceId: UUID)

object Events extends SprayJsonSupport with DefaultJsonProtocol {
  implicit def archiveEventFormat: RootJsonFormat[ArchiveEvent] = jsonFormat2(ArchiveEvent)
}
