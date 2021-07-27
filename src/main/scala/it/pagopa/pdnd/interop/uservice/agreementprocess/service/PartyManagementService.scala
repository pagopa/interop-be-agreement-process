package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import scala.concurrent.Future

trait PartyManagementService {
  def getConsumerAttributes(bearerToken: String, agreementId: String): Future[Seq[String]]
}
