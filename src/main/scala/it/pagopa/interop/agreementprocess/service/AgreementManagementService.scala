package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.{model => AgreementProcess}
import it.pagopa.interop.catalogmanagement.client.model.{Attribute, AttributeValue, Attributes, EService}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait AgreementManagementService {

  def createAgreement(headers: Map[String, String])(
    producerId: UUID,
    agreementPayload: AgreementProcess.AgreementPayload,
    verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
  ): Future[Agreement]

  def getAgreementById(headers: Map[String, String])(agreementId: String): Future[Agreement]

  def getAgreements(headers: Map[String, String])(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  ): Future[Seq[Agreement]]

  def activateById(
    headers: Map[String, String]
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement]
  def suspendById(
    headers: Map[String, String]
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement]

  def markVerifiedAttribute(
    headers: Map[String, String]
  )(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed): Future[Agreement]

  def upgradeById(headers: Map[String, String])(agreementId: UUID, agreementSeed: AgreementSeed): Future[Agreement]
}

object AgreementManagementService {

  def validatePayload(
    payload: AgreementProcess.AgreementPayload,
    agreements: Seq[Agreement]
  ): Future[AgreementProcess.AgreementPayload] = {

    val isValidPayload = !agreements.exists(existsAgreement(payload))

    Future.fromTry(
      Either
        .cond(
          isValidPayload,
          payload,
          new RuntimeException(
            s"Consumer ${payload.consumerId} already has an active agreement for eservice/descriptor ${payload.eserviceId}/${payload.descriptorId}"
          )
        )
        .toTry
    )
  }

  private def existsAgreement(payload: AgreementProcess.AgreementPayload): Agreement => Boolean = agreement => {
    agreement.consumerId == payload.consumerId &&
      agreement.eserviceId == payload.eserviceId &&
      agreement.descriptorId == payload.descriptorId &&
      agreement.state == AgreementState.ACTIVE
  }

  def getStateChangeDetails(agreement: Agreement, partyId: String): Future[StateChangeDetails] = {
    val consumerId = agreement.consumerId.toString
    val producerId = agreement.producerId.toString

    partyId match {
      case `consumerId` =>
        Future.successful(StateChangeDetails(changedBy = Some(ChangedBy.CONSUMER)))
      case `producerId` =>
        Future.successful(StateChangeDetails(changedBy = Some(ChangedBy.PRODUCER)))
      case _ =>
        Future.failed(new RuntimeException("the party doing the operation is neither consumer nor producer"))
    }
  }

  def isActive(agreement: Agreement): Future[Agreement] =
    agreement.state match {
      case AgreementState.ACTIVE =>
        Future.successful(agreement)
      case _ =>
        Future.failed(new RuntimeException(s"Agreement ${agreement.id} state is ${agreement.state}"))
    }

  def isPending(agreement: Agreement): Future[Agreement] =
    agreement.state match {
      case AgreementState.PENDING =>
        Future.successful(agreement)
      case _ =>
        Future.failed(new RuntimeException(s"Agreement ${agreement.id} state is ${agreement.state}"))
    }

  def isSuspended(agreement: Agreement): Future[Agreement] =
    agreement.state match {
      case AgreementState.SUSPENDED =>
        Future.successful(agreement)
      case _ =>
        Future.failed(new RuntimeException(s"Agreement ${agreement.id} state is ${agreement.state}"))
    }

  def verifyCertifiedAttributes(consumerAttributesIds: Seq[String], eservice: EService): Future[EService] = {
    val isActivatable: Boolean = hasAllAttributes(consumerAttributesIds)(eservice.attributes.certified)

    if (isActivatable) Future.successful(eservice)
    else Future.failed[EService](new RuntimeException("This consumer does not have certified attributes"))
  }

  def verifyAttributes(
    consumerAttributesIds: Seq[String],
    eserviceAttributes: Attributes,
    agreementVerifiedAttributes: Seq[VerifiedAttribute]
  ): Future[Boolean] = {

    def hasCertified: Boolean = hasAllAttributes(consumerAttributesIds)(eserviceAttributes.certified)

    def hasVerified: Boolean = {
      val verifiedAttributes: Seq[String] =
        agreementVerifiedAttributes.filter(_.verified.contains(true)).map(_.id.toString)
      hasAllAttributes(verifiedAttributes)(eserviceAttributes.verified)
    }

    if (hasCertified && hasVerified)
      Future.successful(true)
    else
      Future.failed[Boolean](new RuntimeException("This agreement does not have all the expected attributes"))
  }

  private def hasAllAttributes(consumerAttributes: Seq[String])(attributes: Seq[Attribute]): Boolean = {
    attributes.map(hasAttribute(consumerAttributes)).forall(identity)
  }

  private def hasAttribute(consumerAttributes: Seq[String])(attribute: Attribute): Boolean = {
    val hasSingleAttribute = attribute.single.forall(single => consumerAttributes.contains(single.id))

    val hasGroupAttributes =
      attribute.group.forall(orAttributes => consumerAttributes.intersect(orAttributes.map(_.id)).nonEmpty)

    hasSingleAttribute && hasGroupAttributes
  }

  //TODO this function must be improve with
  // - check attribute validityTimespan
  // - a specific behaviour when the same attribute has a different verified value (e.g. send notification)
  def extractVerifiedAttribute(agreements: Seq[Agreement]): Future[Set[UUID]] = Future.successful {
    val allVerifiedAttribute: Seq[VerifiedAttribute] = agreements.flatMap(agreement => agreement.verifiedAttributes)

    // We are excluding for now cases where we can find opposite verification for the same attribute
    // Need to verify the right behaviour
    allVerifiedAttribute
      .groupBy(_.id)
      .filter { case (_, attrs) => attrs.nonEmpty && attrs.forall(_.verified.contains(true)) }
      .keys
      .toSet

  }

  def applyImplicitVerification(verifiedAttributes: Seq[AttributeValue], consumerVerifiedAttributes: Set[UUID])(implicit
    ec: ExecutionContext
  ): Future[Seq[VerifiedAttributeSeed]] = {
    Future.traverse(verifiedAttributes)(verifiedAttribute =>
      createVerifiedAttributeSeeds(verifiedAttribute, consumerVerifiedAttributes)
    )
  }

  private def createVerifiedAttributeSeeds(
    attribute: AttributeValue,
    consumerVerifiedAttributes: Set[UUID]
  ): Future[VerifiedAttributeSeed] =
    Future.fromTry {
      val isImplicitVerifications: Boolean = !attribute.explicitAttributeVerification
      attribute.id.toUUID.map { uuid =>
        if (isImplicitVerifications && consumerVerifiedAttributes.contains(uuid))
          VerifiedAttributeSeed(uuid, Some(true))
        else VerifiedAttributeSeed(uuid, verified = None)
      }

    }

  def agreementStateToApi(status: AgreementState): AgreementProcess.AgreementState =
    status match {
      case AgreementState.ACTIVE    => AgreementProcess.AgreementState.ACTIVE
      case AgreementState.PENDING   => AgreementProcess.AgreementState.PENDING
      case AgreementState.SUSPENDED => AgreementProcess.AgreementState.SUSPENDED
      case AgreementState.INACTIVE  => AgreementProcess.AgreementState.INACTIVE
    }
}
