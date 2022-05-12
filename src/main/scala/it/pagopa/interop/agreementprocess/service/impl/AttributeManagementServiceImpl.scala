package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{
  AttributeManagementService,
  AttributeRegistryManagementInvoker,
  ClientAttribute
}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, StringOps}
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

final case class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  implicit val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
    for {
      uuid      <- attributeId.toFutureUUID
      attribute <- attributeByUUID(uuid)
    } yield attribute

  private def attributeByUUID(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributeById(correlationId, attributeId, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Retrieving attribute by id = $attributeId")
    } yield result

  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[ClientAttribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAttributeByOriginAndCode(correlationId, origin, code, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Retrieving attribute by origin = $origin and code = $code")
  } yield result

}
