package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.Logger
import it.pagopa.interop.agreementprocess.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.authorizationmanagement.client.invoker.BearerToken
import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientAgreementDetailsUpdate,
  ClientComponentState
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, api: PurposeApi)(implicit
  ec: ExecutionContext
) extends AuthorizationManagementService {

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

  override def updateAgreementAndEServiceStates(
    eServiceId: UUID,
    consumerId: UUID,
    payload: ClientAgreementAndEServiceDetailsUpdate
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.updateAgreementAndEServiceStates(
      correlationId,
      eserviceId = eServiceId,
      consumerId = consumerId,
      clientAgreementAndEServiceDetailsUpdate = payload,
      ip
    )(BearerToken(bearerToken))
    invoker
      .invoke(request, s"Update Agreement and EService states on all clients")
      .recoverWith { case _ => Future.unit } // Do not fail because this service should not be blocked by this update

  }

}
