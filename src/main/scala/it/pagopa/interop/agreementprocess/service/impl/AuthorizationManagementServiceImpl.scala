package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{
  AuthorizationManagementInvoker,
  AuthorizationManagementPurposeApi,
  AuthorizationManagementService
}
import it.pagopa.interop.authorizationmanagement.client.invoker.BearerToken
import it.pagopa.interop.authorizationmanagement.client.model.{ClientAgreementDetailsUpdate, ClientComponentState}
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AuthorizationManagementServiceImpl(
  invoker: AuthorizationManagementInvoker,
  api: AuthorizationManagementPurposeApi
)(implicit ec: ExecutionContext)
    extends AuthorizationManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def updateStateOnClients(
    contexts: Seq[(String, String)]
  )(eServiceId: UUID, consumerId: UUID, state: ClientComponentState): Future[Unit] = {
    val payload: ClientAgreementDetailsUpdate = ClientAgreementDetailsUpdate(state = state)

    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateAgreementState(
        correlationId,
        eserviceId = eServiceId,
        consumerId = consumerId,
        clientAgreementDetailsUpdate = payload,
        ip
      )(BearerToken(bearerToken))
      result <- invoker
        .invoke(request, s"Update Agreement state on all clients")
        .recoverWith { case _ =>
          Future.successful(())
        } // Do not fail because this service should not be blocked by this update
    } yield result

  }
}
