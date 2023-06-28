package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.CatalogManagementService
import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.EServiceNotFound
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.agreementprocess.common.readmodel.ReadModelCatalogQueries

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final object CatalogManagementServiceImpl extends CatalogManagementService {

  override def getEServiceById(
    eserviceId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[CatalogItem] =
    ReadModelCatalogQueries.getEServiceById(eserviceId).flatMap(_.toFuture(EServiceNotFound(eserviceId)))
}
