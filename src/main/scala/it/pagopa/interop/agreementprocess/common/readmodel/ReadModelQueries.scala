package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.model.AgreementState
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

object ReadModelQueries {
  def listAgreements(
    requesterId: UUID,
    eServicesIds: List[String],
    consumersIds: List[String],
    producersIds: List[String],
    states: List[AgreementState],
    offset: Int,
    limit: Int
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[PaginatedResult[PersistentAgreement]] = ???
}
