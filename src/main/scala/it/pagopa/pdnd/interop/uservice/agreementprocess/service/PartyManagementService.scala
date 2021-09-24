package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {

  def getPartyAttributes(bearerToken: String)(partyId: String): Future[Seq[String]]
  def getOrganization(bearerToken: String)(partyId: UUID): Future[Organization]

}
