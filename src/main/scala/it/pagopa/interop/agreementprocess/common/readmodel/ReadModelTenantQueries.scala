package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.tenantmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.model.Filters

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

object ReadModelTenantQueries extends ReadModelQuery {

  def getTenant(
    tenantId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Option[PersistentTenant]] = {
    readModel.findOne[PersistentTenant]("tenants", Filters.eq("data.id", tenantId.toString))
  }
}
