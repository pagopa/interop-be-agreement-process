package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.partymanagement.client.model.{Attribute, Organization}
import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {

  def getPartyAttributes(bearerToken: String)(partyId: UUID): Future[Seq[Attribute]]
  def getOrganization(bearerToken: String)(partyId: UUID): Future[Organization]

}
