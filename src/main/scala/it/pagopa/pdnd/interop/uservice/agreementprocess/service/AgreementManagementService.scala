package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement

import scala.concurrent.Future

trait AgreementManagementService {
  def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement]
  def checkAgreementActivation(agreement: Agreement): Future[Agreement]
}
