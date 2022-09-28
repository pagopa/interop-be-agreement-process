package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{
  AuthorizationManagementInvoker,
  AuthorizationManagementPurposeApi,
  AuthorizationManagementService
}
import it.pagopa.interop.authorizationmanagement.client.invoker.BearerToken
import it.pagopa.interop.authorizationmanagement.client.model.{ClientAgreementDetailsUpdate, ClientComponentState}
import it.pagopa.interop.commons.utils.withHeaders
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AuthorizationManagementServiceImpl(
  invoker: AuthorizationManagementInvoker,
  api: AuthorizationManagementPurposeApi
)(implicit ec: ExecutionContext)
    extends AuthorizationManagementService {

  implicit val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def updateStateOnClients(eServiceId: UUID, consumerId: UUID, agreementId: UUID, state: ClientComponentState)(
    implicit contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.updateAgreementState(
        correlationId,
        eserviceId = eServiceId,
        consumerId = consumerId,
        clientAgreementDetailsUpdate = ClientAgreementDetailsUpdate(agreementId = agreementId, state = state),
        ip
      )(BearerToken(bearerToken))
      invoker
        .invoke(request, s"Update Agreement state on all clients")
        .recoverWith { case _ =>
          Future.unit
        } // Do not fail because this service should not be blocked by this update
    }

}
