package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  AgreementEnums,
  AgreementSeed,
  VerifiedAttribute,
  VerifiedAttributeSeed
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.error.{
  ActiveAgreementAlreadyExists,
  AgreementAttributeNotFound,
  AgreementNotFound,
  DescriptorNotFound
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorEnums
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  AttributeValue => CatalogAttributeValue,
  EService => CatalogEService
}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ToString",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Recursion"
  )
)
class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeManagementService: AttributeManagementService
)(implicit ec: ExecutionContext)
    extends AgreementApiService {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def activateAgreement(agreementId: String, partyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Activating agreement $agreementId")
    val result = for {
      bearerToken           <- extractBearer(contexts)
      agreement             <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      _                     <- verifyAgreementActivationEligibility(bearerToken)(agreement)
      consumerAttributesIds <- partyManagementService.getPartyAttributes(bearerToken)(agreement.consumerId)
      eservice              <- catalogManagementService.getEServiceById(bearerToken)(agreement.eserviceId)
      activeEservice        <- CatalogManagementService.validateActivationOnDescriptor(eservice, agreement.descriptorId)
      _ <- AgreementManagementService.verifyAttributes(
        consumerAttributesIds,
        activeEservice.attributes,
        agreement.verifiedAttributes
      )
      changeStatusDetails <- AgreementManagementService.getStatusChangeDetails(agreement, partyId)
      _                   <- agreementManagementService.activateById(bearerToken)(agreementId, changeStatusDetails)
    } yield ()

    onComplete(result) {
      case Success(_) => activateAgreement204
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while activating agreement $agreementId")
        activateAgreement400(errorResponse)
    }
  }

  /** Code: 204, Message: Active agreement suspended.
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def suspendAgreement(agreementId: String, partyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Suspending agreement $agreementId")
    val result = for {
      bearerToken         <- extractBearer(contexts)
      agreement           <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      _                   <- AgreementManagementService.isActive(agreement)
      changeStatusDetails <- AgreementManagementService.getStatusChangeDetails(agreement, partyId)
      _                   <- agreementManagementService.suspendById(bearerToken)(agreementId, changeStatusDetails)
    } yield ()

    onComplete(result) {
      case Success(_) => suspendAgreement204
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while suspending agreement $agreementId")
        suspendAgreement400(errorResponse)
    }
  }

  /** Code: 201, Message: Agreement created., DataType: Agreement
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def createAgreement(agreementPayload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {

    logger.info(s"Creating agreement $agreementPayload")
    val result = for {
      bearerToken <- extractBearer(contexts)
      consumerAgreements <- agreementManagementService.getAgreements(bearerToken = bearerToken)(consumerId =
        Some(agreementPayload.consumerId.toString)
      )
      validPayload               <- AgreementManagementService.validatePayload(agreementPayload, consumerAgreements)
      eservice                   <- catalogManagementService.getEServiceById(bearerToken)(validPayload.eserviceId)
      activeEservice             <- CatalogManagementService.validateOperationOnDescriptor(eservice, agreementPayload.descriptorId)
      consumer                   <- partyManagementService.getOrganization(bearerToken)(agreementPayload.consumerId)
      activatableEservice        <- AgreementManagementService.verifyCertifiedAttributes(consumer.attributes, activeEservice)
      consumerVerifiedAttributes <- AgreementManagementService.extractVerifiedAttribute(consumerAgreements)
      verifiedAttributes         <- CatalogManagementService.flattenAttributes(activatableEservice.attributes.verified)
      verifiedAttributeSeeds <- AgreementManagementService.applyImplicitVerification(
        verifiedAttributes,
        consumerVerifiedAttributes
      )
      agreement <- agreementManagementService.createAgreement(bearerToken)(
        activatableEservice.producerId,
        validPayload,
        verifiedAttributeSeeds
      )
      apiAgreement <- getApiAgreement(bearerToken)(agreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => createAgreement201(agreement)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while creating agreement $agreementPayload")
        createAgreement400(errorResponse)
    }
  }

  /** Code: 200, Message: Agreement created., DataType: Seq[Agreement]
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    status: Option[String],
    latest: Option[Boolean]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Seq[Agreement]] = for {
      bearerToken <- extractBearer(contexts)
      agreements <- agreementManagementService.getAgreements(bearerToken = bearerToken)(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        status = status
      )
      apiAgreementsWithVersion <- Future.traverse(agreements)(getApiAgreement(bearerToken))
      apiAgreements            <- AgreementFilter.filterAgreementsByLatestVersion(latest, apiAgreementsWithVersion)
    } yield apiAgreements

    onComplete(result) {
      case Success(agreement) => getAgreements200(agreement)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while retrieving agreements with filters")
        getAgreements400(errorResponse)
    }
  }

  /** Code: 200, Message: agreement found, DataType: Agreement
    * Code: 400, Message: Invalid ID supplied, DataType: Problem
    */
  override def getAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val result: Future[Agreement] = for {
      bearerToken  <- extractBearer(contexts)
      agreement    <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      apiAgreement <- getApiAgreement(bearerToken)(agreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => getAgreementById200(agreement)
      case Failure(exception) =>
        exception match {
          case ex: AgreementNotFound =>
            val errorResponse: Problem =
              Problem(Option(ex.getMessage), 404, s"Agreement $agreementId not found")
            getAgreementById404(errorResponse)
          case ex =>
            val errorResponse: Problem =
              Problem(Option(ex.getMessage), 400, s"Error while retrieving agreement $agreementId")
            getAgreementById400(errorResponse)
        }
    }
  }

  private def getApiAgreement(bearerToken: String)(agreement: ManagementAgreement): Future[Agreement] = {
    for {
      eservice <- catalogManagementService.getEServiceById(bearerToken)(agreement.eserviceId)
      descriptor <- eservice.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(agreement.eserviceId.toString, agreement.descriptorId.toString))
      activeDescriptorOption <- CatalogManagementService.getActiveDescriptorOption(eservice, descriptor)
      producer               <- partyManagementService.getOrganization(bearerToken)(agreement.producerId)
      consumer               <- partyManagementService.getOrganization(bearerToken)(agreement.consumerId)
      attributes             <- getApiAgreementAttributes(agreement.verifiedAttributes, eservice)
    } yield Agreement(
      id = agreement.id,
      producer = Organization(id = producer.institutionId, name = producer.description),
      consumer = Organization(id = consumer.institutionId, name = consumer.description),
      eserviceDescriptorId = descriptor.id,
      eservice = EService(
        id = eservice.id,
        name = eservice.name,
        version = descriptor.version,
        activeDescriptor =
          activeDescriptorOption.map(d => ActiveDescriptor(id = d.id, status = d.status.toString, version = d.version))
      ),
      status = agreement.status.toString,
      attributes = attributes,
      suspendedByConsumer = agreement.suspendedByConsumer,
      suspendedByProducer = agreement.suspendedByProducer
    )
  }

  private def getApiAgreementAttributes(
    verifiedAttributes: Seq[VerifiedAttribute],
    eService: CatalogEService
  ): Future[Seq[AgreementAttributes]] =
    for {
      attributes <- Future.traverse(verifiedAttributes)(getApiAttribute(eService.attributes))
      eServiceSingleAttributes = eService.attributes.verified.flatMap(_.single)
      eServiceGroupAttributes  = eService.attributes.verified.flatMap(_.group)
      agreementSingleAttributes <- eServiceSingleAttributes.traverse(eServiceToAgreementAttribute(_, attributes))
      agreementGroupAttributes <- eServiceGroupAttributes.traverse(
        _.traverse(eServiceToAgreementAttribute(_, attributes))
      )
      apiSingleAttributes = agreementSingleAttributes.map(single => AgreementAttributes(Some(single), None))
      apiGroupAttributes  = agreementGroupAttributes.map(group => AgreementAttributes(None, Some(group)))
    } yield apiSingleAttributes ++ apiGroupAttributes

  private def eServiceToAgreementAttribute(
    eServiceAttributeValue: CatalogAttributeValue,
    agreementAttributes: Seq[Attribute]
  ): Future[Attribute] =
    agreementAttributes
      .find(_.id.toString == eServiceAttributeValue.id)
      .toFuture(AgreementAttributeNotFound(eServiceAttributeValue.id))

  private def getApiAttribute(
    attributes: ManagementAttributes
  )(verifiedAttribute: VerifiedAttribute): Future[Attribute] = {
    val fromSingle: Seq[CatalogAttributeValue] =
      attributes.verified.flatMap(attribute => attribute.single.toList.find(_.id == verifiedAttribute.id.toString))

    val fromGroup: Seq[CatalogAttributeValue] =
      attributes.verified.flatMap(attribute => attribute.group.flatMap(_.find(_.id == verifiedAttribute.id.toString)))

    val allVerifiedAttributes: Map[String, Boolean] =
      (fromSingle ++ fromGroup).map(attribute => attribute.id -> attribute.explicitAttributeVerification).toMap

    for {
      att  <- attributeManagementService.getAttribute(verifiedAttribute.id.toString)
      uuid <- Future.fromTry(Try(UUID.fromString(att.id)))
    } yield Attribute(
      id = uuid,
      code = att.code,
      description = att.description,
      origin = att.origin,
      name = att.name,
      explicitAttributeVerification = allVerifiedAttributes.get(att.id),
      verified = verifiedAttribute.verified,
      verificationDate = verifiedAttribute.verificationDate,
      validityTimespan = verifiedAttribute.validityTimespan
    )

  }

  /** Code: 204, Message: No Content
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def verifyAgreementAttribute(agreementId: String, attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Marking agreement $agreementId verified attribute $attributeId as verified.")

    val result = for {
      bearerToken <- extractBearer(contexts)
      attributeUUID <- Future.fromTry(Try {
        UUID.fromString(attributeId)
      })
      _ <- agreementManagementService.markVerifiedAttribute(bearerToken)(
        agreementId,
        VerifiedAttributeSeed(attributeUUID, verified = Some(true))
      )
    } yield ()

    onComplete(result) {
      case Success(_) => verifyAgreementAttribute204
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while verifying agreement $agreementId attribute $attributeId")
        verifyAgreementAttribute404(errorResponse)
    }
  }

  /** Verify if an agreement can be activated.
    * Checks performed:
    * - no other active agreement exists for the same combination of Producer, Consumer, EService, Descriptor
    * - the given agreement is in status Pending (first activation) or Suspended (re-activation)
    * @param bearerToken auth token
    * @param agreement to be activated
    * @return
    */
  private def verifyAgreementActivationEligibility(
    bearerToken: String
  )(agreement: ManagementAgreement): Future[Unit] = {
    for {
      activeAgreement <- agreementManagementService.getAgreements(bearerToken)(
        producerId = Some(agreement.producerId.toString),
        consumerId = Some(agreement.consumerId.toString),
        eserviceId = Some(agreement.eserviceId.toString),
        descriptorId = Some(agreement.descriptorId.toString),
        status = Some(AgreementEnums.Status.Active.toString)
      )
      _ <- Either.cond(activeAgreement.isEmpty, (), ActiveAgreementAlreadyExists(agreement)).toFuture
      _ <- AgreementManagementService.isPending(agreement).recoverWith { case _ =>
        AgreementManagementService.isSuspended(agreement)
      }
    } yield ()
  }

  /** Code: 200, Message: Agreement updated., DataType: Agreement
    * Code: 404, Message: Agreement not found, DataType: Problem
    * Code: 400, Message: Invalid ID supplied, DataType: Problem
    */
  override def upgradeAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val result = for {
      bearerToken <- extractBearer(contexts)
      agreement   <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      eservice    <- catalogManagementService.getEServiceById(bearerToken)(agreement.eserviceId)
      latestActiveEserviceDescriptor <- eservice.descriptors
        .find(d => d.status == EServiceDescriptorEnums.Status.Published)
        .toFuture(DescriptorNotFound(agreement.eserviceId.toString, agreement.descriptorId.toString))
      latestDescriptorVersion = latestActiveEserviceDescriptor.version.toLongOption
      currentVersion          = eservice.descriptors.find(d => d.id == agreement.descriptorId).flatMap(_.version.toLongOption)
      _ <- CatalogManagementService.hasEserviceNewPublishedVersion(latestDescriptorVersion, currentVersion)
      agreementSeed = AgreementSeed(
        eserviceId = eservice.id,
        descriptorId = latestActiveEserviceDescriptor.id,
        producerId = agreement.producerId,
        consumerId = agreement.consumerId,
        verifiedAttributes = agreement.verifiedAttributes.map(v =>
          VerifiedAttributeSeed(id = v.id, verified = v.verified, validityTimespan = v.validityTimespan)
        )
      )
      newAgreement <- agreementManagementService.upgradeById(bearerToken)(agreement.id, agreementSeed)
      apiAgreement <- getApiAgreement(bearerToken)(newAgreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => upgradeAgreementById200(agreement)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while updating agreement $agreementId")
        upgradeAgreementById400(errorResponse)
    }
  }

}
