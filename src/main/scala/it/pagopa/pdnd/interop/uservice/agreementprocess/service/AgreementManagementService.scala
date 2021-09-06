package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  Agreement,
  VerifiedAttribute,
  VerifiedAttributeSeed
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.AgreementPayload
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{AttributeValue, Attributes}

import java.util.UUID
import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait AgreementManagementService {
  def validatePayload(bearerToken: String, payload: AgreementPayload): Future[AgreementPayload]
  def createAgreement(
    bearerToken: String,
    agreementPayload: AgreementPayload,
    flattenedVerifiedAttributes: Seq[VerifiedAttributeSeed]
  ): Future[Agreement]
  def verifyAttributes(
    consumerAttributesIds: Seq[String],
    eServiceAttributes: Attributes,
    verifiedAttributes: Seq[VerifiedAttribute]
  ): Future[Boolean]
  def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement]
  def getAgreements(
    bearerToken: String,
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    status: Option[String] = None
  ): Future[Seq[Agreement]]
  def activateById(bearerToken: String, agreementId: String): Future[Agreement]
  def checkAgreementActivation(agreement: Agreement): Future[Agreement]
  def isPending(agreement: Agreement): Future[Agreement]
  def markAttributeAsVerified(bearerToken: String, agreementId: String, attributeId: UUID): Future[Agreement]
  def extractVerifiedAttribute(agreements: Seq[Agreement]): Future[Set[UUID]]
  def applyImplicitVerification(
    verifiedAttributes: Seq[AttributeValue],
    consumerVerifiedAttributes: Set[UUID]
  ): Future[Seq[VerifiedAttributeSeed]]
}
