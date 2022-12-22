package it.pagopa.interop.agreementprocess.service.impl

import cats.implicits._
import it.pagopa.interop.agreementprocess.service.{
  AttributeManagementService,
  AttributeRegistryManagementInvoker,
  ClientAttribute
}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
import it.pagopa.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

final class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  implicit val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
    attributeId.toFutureUUID >>= attributeByUUID

  private def attributeByUUID(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.getAttributeById(correlationId, attributeId, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving attribute by id = $attributeId")
    }

}
