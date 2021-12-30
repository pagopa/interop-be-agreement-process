package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AttributeManagementService,
  AttributeRegistryManagementInvoker,
  ClientAttribute
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.invoker.ApiError
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.invoker.BearerToken

final case class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttribute(bearerToken: String)(attributeId: String): Future[ClientAttribute] = for {
    uuid      <- attributeId.toFutureUUID
    attribute <- attributeByUUID(bearerToken)(uuid)
  } yield attribute

  private def attributeByUUID(bearerToken: String)(attributeId: UUID): Future[ClientAttribute] = {
    val request = api.getAttributeById(attributeId)(BearerToken(bearerToken)) // TODO maybe a batch request is better
    invoker
      .execute(request)
      .map { x =>
        logger.info(s"Retrieving attribute ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith {
        case ex @ ApiError(code, message, response, error, _) =>
          logger.error(
            s"Retrieving attribute by UUID FAILED. code > $code - message > $message - response > $response",
            error
          )
          Future.failed[ClientAttribute](ex)
        case ex =>
          logger.error("Retrieving attribute by UUID FAILED", ex)
          Future.failed[ClientAttribute](ex)
      }
  }

}
