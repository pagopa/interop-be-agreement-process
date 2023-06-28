package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.commons.cqrs.service.ReadModelService

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

trait TenantManagementService {
  def getTenantById(
    tenantId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentTenant]
}
