package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.BearerToken
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getEServiceById(contexts: Seq[(String, String)])(eserviceId: UUID): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEService(correlationId, eserviceId.toString, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Getting e-service by id ${eserviceId}")
    } yield result
  }
}
