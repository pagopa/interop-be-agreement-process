package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def updateStateOnClients(eServiceId: UUID, consumerId: UUID, agreementId: UUID, state: ClientComponentState)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

}
