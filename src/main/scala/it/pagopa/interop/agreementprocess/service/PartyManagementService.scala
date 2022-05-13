package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.partymanagement.client.model.{Attribute, Institution}
import java.util.UUID
import scala.concurrent.Future

trait PartyManagementService {

  def getPartyAttributes(bearerToken: String)(partyId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[Attribute]]
  def getInstitution(bearerToken: String)(partyId: UUID)(implicit contexts: Seq[(String, String)]): Future[Institution]

}
