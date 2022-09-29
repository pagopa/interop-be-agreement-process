package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{
  AuthorizationManagementInvoker,
  AuthorizationManagementPurposeApi,
  AuthorizationManagementService
}
import it.pagopa.interop.authorizationmanagement.client.invoker.BearerToken
import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientAgreementDetailsUpdate,
  ClientComponentState
}
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
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
  ): Future[Unit] = {
    val payload: ClientAgreementDetailsUpdate = ClientAgreementDetailsUpdate(agreementId = agreementId, state = state)
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
        .recoverWith { case _ => Future.unit } // Do not fail because this service should not be blocked by this update
    } yield result
  }

  override def updateAgreementAndEServiceStates(
    eServiceId: UUID,
    consumerId: UUID,
    payload: ClientAgreementAndEServiceDetailsUpdate
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateAgreementAndEServiceStates(
        correlationId,
        eserviceId = eServiceId,
        consumerId = consumerId,
        clientAgreementAndEServiceDetailsUpdate = payload,
        ip
      )(BearerToken(bearerToken))
      result <- invoker
        .invoke(request, s"Update Agreement and EService states on all clients")
        .recoverWith { case _ => Future.unit } // Do not fail because this service should not be blocked by this update
    } yield result
  }
}
