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
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttribute(contexts: Seq[(String, String)])(attributeId: String): Future[ClientAttribute] = for {
    uuid      <- attributeId.toFutureUUID
    attribute <- attributeByUUID(contexts)(uuid)
  } yield attribute

  private def attributeByUUID(contexts: Seq[(String, String)])(attributeId: UUID): Future[ClientAttribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAttributeById(correlationId, attributeId, ip)(
      BearerToken(bearerToken)
    ) // TODO maybe a batch request is better
    result <- invoker.invoke(request, s"Retrieving attribute by id = $attributeId")
  } yield result

  override def getAttributeByOriginAndCode(
    contexts: Seq[(String, String)]
  )(origin: String, code: String): Future[ClientAttribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAttributeByOriginAndCode(correlationId, origin, code, ip)(
      BearerToken(bearerToken)
    ) // TODO maybe a batch request is better
    result <- invoker.invoke(request, s"Retrieving attribute by origin = $origin and code = $code")
  } yield result

}
