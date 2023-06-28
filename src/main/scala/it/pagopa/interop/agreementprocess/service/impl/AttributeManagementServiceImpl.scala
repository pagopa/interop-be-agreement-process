package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.AttributeManagementService
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.AttributeNotFound
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.agreementprocess.common.readmodel.ReadModelAttributeQueries

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final object AttributeManagementServiceImpl extends AttributeManagementService {

  override def getAttributeById(
    attributeId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAttribute] =
    ReadModelAttributeQueries.getAttributeById(attributeId).flatMap(_.toFuture(AttributeNotFound(attributeId)))
}
