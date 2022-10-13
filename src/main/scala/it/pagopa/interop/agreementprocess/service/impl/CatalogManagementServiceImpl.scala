package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.utils.withHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.EServiceNotFound
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEServiceById(eserviceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.getEService(correlationId, eserviceId.toString, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Getting e-service by id $eserviceId").recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eserviceId))
      }
    }

}
