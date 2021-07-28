package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, VerifiedAttribute}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{AgreementPayload}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.Attributes

import java.util.UUID
import scala.concurrent.Future

trait AgreementManagementService {
  def createAgreement(
    bearerToken: String,
    agreementPayload: AgreementPayload,
    flattenedVerifiedAttributes: Seq[UUID]
  ): Future[Agreement]
  def verifyAttributes(
    consumerAttributesIds: Seq[String],
    eServiceAttributes: Attributes,
    verifiedAttributes: Seq[VerifiedAttribute]
  ): Future[Boolean]
  def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement]
  def activateById(bearerToken: String, agreementId: String): Future[Agreement]
  def checkAgreementActivation(agreement: Agreement): Future[Agreement]
  def isPending(agreement: Agreement): Future[Agreement]
  def markAttributeAsVerified(bearerToken: String, agreementId: String, attributeId: UUID): Future[Agreement]
}
