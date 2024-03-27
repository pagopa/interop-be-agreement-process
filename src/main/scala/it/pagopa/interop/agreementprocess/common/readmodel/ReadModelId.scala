package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.commons.queue.message.Message.uuidFormat
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import java.util.UUID

final case class ReadModelId(id: UUID)

object ReadModelId {
  implicit val rmiFormat: RootJsonFormat[ReadModelId] = jsonFormat1(ReadModelId.apply)
}
