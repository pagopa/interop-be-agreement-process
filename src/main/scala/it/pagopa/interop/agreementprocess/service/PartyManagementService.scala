package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.selfcare.partymanagement.client.model.{Attribute, Institution}

import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {

  def getPartyAttributes(partyId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[Attribute]]

  def getInstitution(partyId: UUID)(implicit contexts: Seq[(String, String)]): Future[Institution]

}
