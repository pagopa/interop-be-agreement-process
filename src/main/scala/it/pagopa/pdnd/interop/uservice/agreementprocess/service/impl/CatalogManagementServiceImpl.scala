package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors.{ResourceConflictError, ResourceNotFoundError}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EService
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)
    extends CatalogManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getEServiceById(bearerToken: String)(eserviceId: UUID): Future[EService] = {
    val request: ApiRequest[EService] = api.getEService(eserviceId.toString)(BearerToken(bearerToken))
    invoker.invoke(request, s"Getting e-service by id ${eserviceId}", invocationRecovery(eserviceId.toString))
  }

  private def invocationRecovery[T](resourceId: String): (Logger, String) => PartialFunction[Throwable, Future[T]] =
    (logger, message) => {
      case ApiError(code, apiMessage, response, throwable, _) if code == 409 =>
        logger.error(
          "{} FAILED. code > {} - message > {} - response > {}",
          message,
          code,
          apiMessage,
          response,
          throwable
        )
        Future.failed[T](ResourceConflictError(resourceId))
      case ApiError(code, apiMessage, response, throwable, _) if code == 404 =>
        logger.error(
          "{} FAILED. code > {} - message > {} - response > {}",
          message,
          code,
          apiMessage,
          response,
          throwable
        )
        Future.failed[T](ResourceNotFoundError(resourceId))
      case ApiError(code, apiMessage, response, throwable, _) =>
        logger.error(
          "{} FAILED. code > {} - message > {} - response > {}",
          message,
          code,
          apiMessage,
          response,
          throwable
        )
        Future.failed[T](new RuntimeException(message))
      case ex =>
        logger.error(s"Error: ${ex.getMessage}")
        Future.failed[T](ex)
    }

}
