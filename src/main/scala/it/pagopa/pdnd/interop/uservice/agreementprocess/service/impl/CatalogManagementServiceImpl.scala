package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EService
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getEServiceById(bearerToken: String)(eserviceId: UUID): Future[EService] = {
    val request: ApiRequest[EService] = api.getEService(eserviceId.toString)(BearerToken(bearerToken))
    invoker
      .execute[EService](request)
      .map { x =>
        logger.info(s"Retrieving e-service ${x.code}")
        logger.info(s"Retrieving e-service ${x.content}")
        x.content
      }
      .recoverWith {
        case ex @ ApiError(code, message, response, error, _) =>
          logger.error(s"Retrieving e-service FAILED. code > $code - message > $message - response > $response", error)
          Future.failed[EService](ex)
        case ex =>
          logger.error("Retrieving e-service FAILED", ex)
          Future.failed[EService](ex)
      }
  }
}
