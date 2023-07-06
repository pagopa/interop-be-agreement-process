package it.pagopa.interop.agreementprocess.events

import it.pagopa.interop.commons.utils.SprayCommonFormats.{uuidFormat, offsetDateTimeFormat}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import java.time.OffsetDateTime

import java.util.UUID

case class ArchiveEvent(agreementId: UUID, createdAt: OffsetDateTime)

object Events extends DefaultJsonProtocol {
  implicit def archiveEventFormat: RootJsonFormat[ArchiveEvent] = jsonFormat2(ArchiveEvent)
}
