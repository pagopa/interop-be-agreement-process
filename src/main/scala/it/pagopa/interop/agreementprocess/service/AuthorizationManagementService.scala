package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def updateStateOnClients(bearerToken: String)(agreementId: UUID, state: ClientComponentState): Future[Unit]

}