package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.AgreementPayload
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, AttributeValue, Attributes, EService}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait AgreementManagementService {

  def createAgreement(bearerToken: String)(
    producerId: UUID,
    agreementPayload: AgreementPayload,
    verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
  ): Future[Agreement]

  def getAgreementById(bearerToken: String)(agreementId: String): Future[Agreement]

  def getAgreements(bearerToken: String)(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    status: Option[String] = None
  ): Future[Seq[Agreement]]

  def activateById(
    bearerToken: String
  )(agreementId: String, statusChangeDetails: StatusChangeDetails): Future[Agreement]
  def suspendById(bearerToken: String)(agreementId: String, statusChangeDetails: StatusChangeDetails): Future[Agreement]

  def markVerifiedAttribute(
    bearerToken: String
  )(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed): Future[Agreement]

  def upgradeById(bearerToken: String)(agreementId: UUID, agreementSeed: AgreementSeed): Future[Agreement]
}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.ToString"
  )
)
object AgreementManagementService {

  def validatePayload(payload: AgreementPayload, agreements: Seq[Agreement]): Future[AgreementPayload] = {

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

  private def existsAgreement(payload: AgreementPayload): Agreement => Boolean = agreement => {
    agreement.consumerId == payload.consumerId &&
      agreement.eserviceId == payload.eserviceId &&
      agreement.descriptorId == payload.descriptorId &&
      agreement.status == AgreementEnums.Status.Active
  }

  def getStatusChangeDetails(agreement: Agreement, partyId: String): Future[StatusChangeDetails] = {
    val consumerId = agreement.consumerId.toString
    val producerId = agreement.producerId.toString

    if (consumerId == partyId)
      Future.successful(StatusChangeDetails(changedBy = Some(StatusChangeDetailsEnums.ChangedBy.Consumer)))
    else if (producerId == partyId)
      Future.successful(StatusChangeDetails(changedBy = Some(StatusChangeDetailsEnums.ChangedBy.Producer)))
    else
      Future.failed(new RuntimeException("the party doing the operation is neither consumer nor producer"))
  }

  def isActive(agreement: Agreement): Future[Agreement] =
    agreement.status match {
      case AgreementEnums.Status.Active =>
        Future.successful(agreement)
      case _ =>
        Future.failed(new RuntimeException(s"Agreement ${agreement.id} status is ${agreement.status}"))
    }

  def isPending(agreement: Agreement): Future[Agreement] =
    agreement.status match {
      case AgreementEnums.Status.Pending =>
        Future.successful(agreement)
      case _ =>
        Future.failed(new RuntimeException(s"Agreement ${agreement.id} status is ${agreement.status}"))
    }

  def isSuspended(agreement: Agreement): Future[Agreement] =
    agreement.status match {
      case AgreementEnums.Status.Suspended =>
        Future.successful(agreement)
      case _ =>
        Future.failed(new RuntimeException(s"Agreement ${agreement.id} status is ${agreement.status}"))
    }

  def verifyCertifiedAttributes(consumerAttributesIds: Seq[String], eservice: EService): Future[EService] = {
    def hasAllAttributesFn(attributes: Seq[Attribute]): Boolean = hasAllAttributes(consumerAttributesIds)(attributes)

    val isActivatable: Boolean = hasAllAttributesFn(eservice.attributes.certified)

    if (isActivatable) Future.successful(eservice)
    else Future.failed[EService](new RuntimeException("This consumer does not have certified attributes"))
  }

  def verifyAttributes(
    consumerAttributesIds: Seq[String],
    eserviceAttributes: Attributes,
    agreementVerifiedAttributes: Seq[VerifiedAttribute]
  ): Future[Boolean] = {

    def hasAllAttributesFn(attributes: Seq[Attribute]): Boolean = hasAllAttributes(consumerAttributesIds)(attributes)

    def hasCertified: Boolean = hasAllAttributesFn(eserviceAttributes.certified)

    def hasVerified: Boolean = agreementVerifiedAttributes.foldLeft(true)((acc, attr) => attr.verified && acc)

    if (hasCertified && hasVerified)
      Future.successful(true)
    else
      Future.failed[Boolean](new RuntimeException("This agreement does not have all the expected attributes"))
  }

  private def hasAllAttributes(consumerAttributes: Seq[String])(attributes: Seq[Attribute]): Boolean = {
    attributes.map(hasAttribute(consumerAttributes)).forall(identity)
  }

  private def hasAttribute(consumerAttributes: Seq[String])(attribute: Attribute): Boolean = {
    val hasSingleAttribute = () => attribute.single.forall(single => consumerAttributes.contains(single.id))

    val hasGroupAttributes = () =>
      attribute.group.forall(orAttributes => consumerAttributes.intersect(orAttributes.map(_.id)).nonEmpty)

    hasSingleAttribute() && hasGroupAttributes()
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
      .filter { case (_, attrs) => attrs.nonEmpty && attrs.forall(_.verified) }
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
      Try(UUID.fromString(attribute.id)).map { uuid =>
        if (isImplicitVerifications) VerifiedAttributeSeed(uuid, consumerVerifiedAttributes.contains(uuid))
        else VerifiedAttributeSeed(uuid, verified = false)
      }

    }
}
