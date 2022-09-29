package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientComponentState
}

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def updateStateOnClients(eServiceId: UUID, consumerId: UUID, agreementId: UUID, state: ClientComponentState)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def updateAgreementAndEServiceStates(
    eServiceId: UUID,
    consumerId: UUID,
    payload: ClientAgreementAndEServiceDetailsUpdate
  )(implicit contexts: Seq[(String, String)]): Future[Unit]
}
