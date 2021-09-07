package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import scala.concurrent.Future

trait PartyManagementService {
  def getPartyAttributes(bearerToken: String, partyId: String): Future[Seq[String]]
}
