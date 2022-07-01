package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.selfcare.partymanagement.client.model.{Attribute, Institution}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PartyManagementService {

  def getPartyAttributes(
    partyId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]]

  def getInstitution(partyId: UUID)(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]

}
