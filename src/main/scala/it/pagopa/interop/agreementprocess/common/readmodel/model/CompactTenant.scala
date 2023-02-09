package it.pagopa.interop.agreementprocess.common.readmodel.model

// import java.util.UUID
// import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

final case class CompactTenant(id: String)

object CompactTenant {
  implicit val format: RootJsonFormat[CompactTenant] = jsonFormat1(CompactTenant.apply)
}
