package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.TenantIdNotFound
import it.pagopa.interop.agreementprocess.service.TenantManagementService
import it.pagopa.interop.agreementprocess.common.readmodel.ReadModelTenantQueries
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final object TenantManagementServiceImpl extends TenantManagementService {

  override def getTenantById(
    tenantId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentTenant] =
    ReadModelTenantQueries.getTenant(tenantId).flatMap(_.toFuture(TenantIdNotFound(tenantId)))
}
