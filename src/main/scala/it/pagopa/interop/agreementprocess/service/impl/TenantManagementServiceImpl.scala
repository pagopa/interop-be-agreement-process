package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.service.{TenantManagementInvoker, TenantManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.tenantmanagement.client.api.TenantApi
import it.pagopa.interop.tenantmanagement.client.invoker.BearerToken
import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID
import scala.concurrent.Future
import it.pagopa.interop.tenantmanagement.client.invoker.ApiRequest

final case class TenantManagementServiceImpl(invoker: TenantManagementInvoker, api: TenantApi)
    extends TenantManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] = withHeaders {
    (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Tenant] = api.getTenant(correlationId, tenantId, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Getting tenant by id $tenantId")
  }
}
