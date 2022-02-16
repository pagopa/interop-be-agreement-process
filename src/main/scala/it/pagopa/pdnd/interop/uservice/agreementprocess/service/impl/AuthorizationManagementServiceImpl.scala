package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AuthorizationManagementInvoker,
  AuthorizationManagementService,
  AuthorizationManagementPurposeApi
}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.model.{ClientAgreementDetailsUpdate, ClientComponentState}
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
    bearerToken: String
  )(agreementId: UUID, state: ClientComponentState): Future[Unit] = {
    val payload: ClientAgreementDetailsUpdate = ClientAgreementDetailsUpdate(state = state)
    val request: ApiRequest[Unit] =
      api.updateAgreementState(agreementId = agreementId, clientAgreementDetailsUpdate = payload)(
        BearerToken(bearerToken)
      )
    invoker
      .invoke(request, s"Update Agreement state on all clients")
      .recoverWith { case _ =>
        Future.successful(())
      } // Do not fail because this service should not be blocked by this update
  }
}
