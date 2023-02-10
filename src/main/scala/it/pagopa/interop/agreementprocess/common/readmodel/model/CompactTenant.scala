package it.pagopa.interop.agreementprocess.common.readmodel.model

import spray.json.DefaultJsonProtocol._
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import spray.json.RootJsonFormat
import java.util.UUID

final case class CompactTenant(id: UUID, name: String)

object CompactTenant {
  implicit val format: RootJsonFormat[CompactTenant] = jsonFormat2(CompactTenant.apply)
}
