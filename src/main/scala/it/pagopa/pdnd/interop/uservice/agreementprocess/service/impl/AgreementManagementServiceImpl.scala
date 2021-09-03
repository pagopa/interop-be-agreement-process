package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.AgreementPayload
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, AttributeValue, Attributes}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.ToString"
  )
)
final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def markAttributeAsVerified(
    bearerToken: String,
    agreementId: String,
    attributeId: UUID
  ): Future[Agreement] = {

    val verifiedAttributeSeed = VerifiedAttributeSeed(id = attributeId, verified = true)
    val request: ApiRequest[Agreement] =
      api.updateAgreementVerifiedAttribute(agreementId, verifiedAttributeSeed)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Attribute verified! agreement ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Attribute verification FAILED: ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def activateById(bearerToken: String, agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.activateAgreement(agreementId)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Activating agreement ${x.code}")
        logger.info(s"Activating agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Activating agreement FAILED: ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Retrieving agreement ${x.code}")
        logger.info(s"Retrieving agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving agreement ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def checkAgreementActivation(agreement: Agreement): Future[Agreement] = {
    Future.fromTry(
      Either
        .cond(
          agreement.status == AgreementEnums.Status.Active,
          agreement,
          new RuntimeException(s"Agreement ${agreement.id} is not active")
        )
        .toTry
    )
  }

  override def isPending(agreement: Agreement): Future[Agreement] = {
    Future.fromTry(
      Either
        .cond(
          agreement.status == AgreementEnums.Status.Pending,
          agreement,
          new RuntimeException(s"Agreement ${agreement.id} status is ${agreement.status}")
        )
        .toTry
    )
  }

  override def verifyAttributes(
    consumerAttributesIds: Seq[String],
    eserviceAttributes: Attributes,
    agreementVerifiedAttributes: Seq[VerifiedAttribute]
  ): Future[Boolean] = {

    def checkAttributesFn(attributes: Seq[Attribute]): Boolean = checkAttributes(consumerAttributesIds)(attributes)

    def hasCertified: Boolean = checkAttributesFn(eserviceAttributes.certified)

    def hasVerified: Boolean = agreementVerifiedAttributes.foldLeft(true)((acc, attr) => attr.verified && acc)

    if (hasCertified && hasVerified)
      Future.successful(true)
    else
      Future.failed[Boolean](new RuntimeException("This agreement does not have all the expected attributes"))
  }

  private def checkAttributes(consumerAttributes: Seq[String])(attributes: Seq[Attribute]): Boolean = {
    attributes.map(hasAttribute(consumerAttributes)).forall(identity)
  }

  private def hasAttribute(consumerAttributes: Seq[String])(attribute: Attribute): Boolean = {
    val hasSingleAttribute = () => attribute.single.forall(single => consumerAttributes.contains(single.id))

    val hasGroupAttributes = () =>
      attribute.group.forall(orAttributes => consumerAttributes.intersect(orAttributes.map(_.id)).nonEmpty)

    hasSingleAttribute() && hasGroupAttributes()
  }

  override def createAgreement(
    bearerToken: String,
    agreementPayload: AgreementPayload,
    verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
  ): Future[Agreement] = {

    val seed: AgreementSeed = AgreementSeed(
      eserviceId = agreementPayload.eserviceId,
      producerId = agreementPayload.producerId,
      consumerId = agreementPayload.consumerId,
      verifiedAttributes = verifiedAttributeSeeds
    )

    val request: ApiRequest[Agreement] = api.addAgreement(seed)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Retrieving agreement ${x.code}")
        logger.info(s"Retrieving agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving agreement ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def validatePayload(bearerToken: String, payload: AgreementPayload): Future[AgreementPayload] = {
    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(
        producerId = Some(payload.producerId.toString),
        consumerId = Some(payload.consumerId.toString),
        eserviceId = Some(payload.eserviceId.toString),
        status = Some("active")
      )(BearerToken(bearerToken))
    invoker
      .execute[Seq[Agreement]](request)
      .flatMap(agreements =>
        Future.fromTry(
          Either
            .cond(
              agreements.content.isEmpty,
              payload,
              new RuntimeException(
                s"Producer ${payload.producerId} already has an active agreement for ${payload.consumerId}"
              )
            )
            .toTry
        )
      )
      .recoverWith { case ex =>
        logger.error(s"Check active agreements failed ${ex.getMessage}")
        Future.failed[AgreementPayload](ex)
      }
  }

  override def getVerifiedAttributes(bearerToken: String, consumerId: UUID): Future[Set[UUID]] = {
    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(consumerId = Some(consumerId.toString), status = Some("active"))(BearerToken(bearerToken))
    invoker
      .execute[Seq[Agreement]](request)
      .map(agreement => extractVerifiedAttribute(agreement.content))
      .recoverWith { case ex =>
        logger.error(s"Error trying to retrieve verified attributes for consumer $consumerId: ${ex.getMessage}")
        Future.failed[Set[UUID]](ex)
      }
  }

  //TODO this function must be improve with
  // - check attribute validityTimespan
  // - a specific behaviour when the same attribute has a different verified value (e.g. send notification)
  private def extractVerifiedAttribute(agreements: Seq[Agreement]): Set[UUID] = {
    val allVerifiedAttribute: Seq[VerifiedAttribute] =
      agreements.flatMap(agreement => agreement.verifiedAttributes.filter(_.verified))

    // We are excluding for now cases where we can find opposite verification for the same attribute
    allVerifiedAttribute
      .groupBy(_.id)
      .filter { case (_, attrs) => attrs.nonEmpty && attrs.forall(_.verified) }
      .keys
      .toSet

  }

  override def applyImplicitVerification(
    verifiedAttributes: Seq[AttributeValue],
    consumerVerifiedAttributes: Set[UUID]
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
      Try(UUID.fromString(attribute.id)).map { uuid =>
        if (consumerVerifiedAttributes.contains(uuid))
          VerifiedAttributeSeed(uuid, !attribute.explicitAttributeVerification)
        else VerifiedAttributeSeed(uuid, false)
      }

    }

}
