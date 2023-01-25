package it.pagopa.interop.agreementprocess.common.readmodel

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

// TODO This should go in commons once the $facet command will be integrated in the aggregate function
final case class TotalCountResult(totalCount: Int)

object TotalCountResult {
  implicit val tcrFormat: RootJsonFormat[TotalCountResult] = jsonFormat1(TotalCountResult.apply)
}
