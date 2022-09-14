package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID
import scala.concurrent.Future

trait TenantManagementService {

  def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
}
