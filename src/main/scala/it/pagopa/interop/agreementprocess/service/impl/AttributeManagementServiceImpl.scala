package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.interop.agreementprocess.service.{
  AttributeManagementService,
  AttributeRegistryManagementInvoker,
  ClientAttribute
}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttribute(bearerToken: String)(attributeId: String): Future[ClientAttribute] = for {
    uuid      <- attributeId.toFutureUUID
    attribute <- attributeByUUID(bearerToken)(uuid)
  } yield attribute

  private def attributeByUUID(bearerToken: String)(attributeId: UUID): Future[ClientAttribute] = {
    val request = api.getAttributeById(attributeId)(BearerToken(bearerToken)) // TODO maybe a batch request is better
    invoker.invoke(request, s"Retrieving attribute by id = $attributeId")
  }

  override def getAttributeByOriginAndCode(
    bearerToken: String
  )(origin: String, code: String): Future[ClientAttribute] = {
    val request =
      api.getAttributeByOriginAndCode(origin, code)(BearerToken(bearerToken)) // TODO maybe a batch request is better
    invoker.invoke(request, s"Retrieving attribute by origin = $origin and code = $code")
  }

}