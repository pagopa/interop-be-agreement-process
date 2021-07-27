package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, VerifiedAttribute}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.Attributes

import scala.concurrent.Future

trait AgreementManagementService {
  def verifyAttributes(
    consumerAttributesIds: Seq[String],        //party
    eServiceAttributes: Attributes,            //eservice
    verifiedAttributes: Seq[VerifiedAttribute] //agreement
  ): Future[Boolean]
  def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement]
  def activateById(bearerToken: String, agreementId: String): Future[Agreement]
  def checkAgreementActivation(agreement: Agreement): Future[Agreement]
  def isPending(agreement: Agreement): Future[Agreement]
}
