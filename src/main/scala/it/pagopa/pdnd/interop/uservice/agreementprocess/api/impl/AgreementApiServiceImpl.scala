package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.{EitherOps, OptionOps, StringOps}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  AgreementSeed,
  VerifiedAttribute,
  VerifiedAttributeSeed
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.AgreementManagementService.agreementStateToApi
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.CatalogManagementService.descriptorStateToApi
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  AttributeValue => CatalogAttributeValue,
  EService => CatalogEService
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.{model => CatalogManagementDependency}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeManagementService: AttributeManagementService,
  jwtReader: JWTReader
)(implicit ec: ExecutionContext)
    extends AgreementApiService {
  val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  override def activateAgreement(agreementId: String, partyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Activating agreement {}", agreementId)
    val result = for {
      bearerToken           <- validateBearer(contexts, jwtReader)
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
      changeStateDetails <- AgreementManagementService.getStateChangeDetails(agreement, partyId)
      _                  <- agreementManagementService.activateById(bearerToken)(agreementId, changeStateDetails)
    } yield ()

    onComplete(result) {
      case Success(_) => activateAgreement204
      case Failure(ex) =>
        logger.error("Error while activating agreement {}", agreementId, ex)
        val errorResponse: Problem = {
          problemOf(StatusCodes.BadRequest, ActivateAgreementError(agreementId))
        }
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
    logger.info("Suspending agreement {}", agreementId)
    val result = for {
      bearerToken        <- validateBearer(contexts, jwtReader)
      agreement          <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      _                  <- AgreementManagementService.isActive(agreement)
      changeStateDetails <- AgreementManagementService.getStateChangeDetails(agreement, partyId)
      _                  <- agreementManagementService.suspendById(bearerToken)(agreementId, changeStateDetails)
    } yield ()

    onComplete(result) {
      case Success(_) => suspendAgreement204
      case Failure(ex) =>
        logger.error("Error while suspending agreement {}", agreementId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, SuspendAgreementError(agreementId))
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
    logger.info("Creating agreement {}", agreementPayload)
    val result = for {
      bearerToken <- validateBearer(contexts, jwtReader)
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
        logger.error("Error while creating agreement {}", agreementPayload, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, CreateAgreementError(agreementPayload))
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
    state: Option[String],
    latest: Option[Boolean]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(
      "Getting agreements by producer = {}, consumer = {}, eservice = {}, descriptor = {}, state = {}, latest = {}",
      producerId,
      consumerId,
      eserviceId,
      descriptorId,
      state,
      latest
    )
    val result: Future[Seq[Agreement]] = for {
      bearerToken <- validateBearer(contexts, jwtReader)
      stateEnum   <- state.traverse(AgreementManagementDependency.AgreementState.fromValue).toFuture
      agreements <- agreementManagementService.getAgreements(bearerToken = bearerToken)(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = stateEnum
      )
      apiAgreementsWithVersion <- Future.traverse(agreements)(getApiAgreement(bearerToken))
      apiAgreements            <- AgreementFilter.filterAgreementsByLatestVersion(latest, apiAgreementsWithVersion)
    } yield apiAgreements

    onComplete(result) {
      case Success(agreement) => getAgreements200(agreement)
      case Failure(ex) =>
        logger.error(
          "Error while getting agreements by producer = {}, consumer = {}, eservice = {}, descriptor = {}, state = {}, latest = {}",
          producerId,
          consumerId,
          eserviceId,
          descriptorId,
          state,
          latest,
          ex
        )
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, RetrieveAgreementsError)
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
    logger.info("Getting agreement by id {}", agreementId)
    val result: Future[Agreement] = for {
      bearerToken  <- validateBearer(contexts, jwtReader)
      agreement    <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      apiAgreement <- getApiAgreement(bearerToken)(agreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => getAgreementById200(agreement)
      case Failure(exception) =>
        logger.error("Error while getting agreement by id {}", agreementId, exception)
        exception match {
          case ex: AgreementNotFound =>
            val errorResponse: Problem =
              problemOf(StatusCodes.NotFound, ex)
            getAgreementById404(errorResponse)
          case _ =>
            val errorResponse: Problem =
              problemOf(StatusCodes.BadRequest, RetrieveAgreementError(agreementId))
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
      attributes             <- getApiAgreementAttributes(bearerToken)(agreement.verifiedAttributes, eservice)
    } yield Agreement(
      id = agreement.id,
      producer = Organization(id = producer.institutionId, name = producer.description),
      consumer = Organization(id = consumer.institutionId, name = consumer.description),
      eserviceDescriptorId = descriptor.id,
      eservice = EService(
        id = eservice.id,
        name = eservice.name,
        version = descriptor.version,
        activeDescriptor = activeDescriptorOption.map(d =>
          ActiveDescriptor(id = d.id, state = descriptorStateToApi(d.state), version = d.version)
        )
      ),
      state = agreementStateToApi(agreement.state),
      attributes = attributes,
      suspendedByConsumer = agreement.suspendedByConsumer,
      suspendedByProducer = agreement.suspendedByProducer
    )
  }

  private def getApiAgreementAttributes(
    bearerToken: String
  )(verifiedAttributes: Seq[VerifiedAttribute], eService: CatalogEService): Future[Seq[AgreementAttributes]] =
    for {
      attributes <- Future.traverse(verifiedAttributes)(getApiAttribute(bearerToken)(eService.attributes))
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
    bearerToken: String
  )(attributes: ManagementAttributes)(verifiedAttribute: VerifiedAttribute): Future[Attribute] = {
    val fromSingle: Seq[CatalogAttributeValue] =
      attributes.verified.flatMap(attribute => attribute.single.toList.find(_.id == verifiedAttribute.id.toString))

    val fromGroup: Seq[CatalogAttributeValue] =
      attributes.verified.flatMap(attribute => attribute.group.flatMap(_.find(_.id == verifiedAttribute.id.toString)))

    val allVerifiedAttributes: Map[String, Boolean] =
      (fromSingle ++ fromGroup).map(attribute => attribute.id -> attribute.explicitAttributeVerification).toMap

    for {
      att  <- attributeManagementService.getAttribute(bearerToken)(verifiedAttribute.id.toString)
      uuid <- att.id.toFutureUUID
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
    logger.info("Verifying agreement {} attribute {}", agreementId, attributeId)
    val result = for {
      bearerToken   <- validateBearer(contexts, jwtReader)
      attributeUUID <- attributeId.toFutureUUID
      _ <- agreementManagementService.markVerifiedAttribute(bearerToken)(
        agreementId,
        VerifiedAttributeSeed(attributeUUID, verified = Some(true))
      )
    } yield ()

    onComplete(result) {
      case Success(_) => verifyAgreementAttribute204
      case Failure(ex) =>
        logger.error("Error while verifying agreement {} attribute {}", agreementId, attributeId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, VerifyAgreementAttributeError(agreementId, attributeId))
        verifyAgreementAttribute404(errorResponse)
    }
  }

  /** Verify if an agreement can be activated.
    * Checks performed:
    * - no other active agreement exists for the same combination of Producer, Consumer, EService, Descriptor
    * - the given agreement is in state Pending (first activation) or Suspended (re-activation)
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
        state = Some(AgreementManagementDependency.AgreementState.ACTIVE)
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
    logger.info("Updating agreement {}", agreementId)
    val result = for {
      bearerToken <- validateBearer(contexts, jwtReader)
      agreement   <- agreementManagementService.getAgreementById(bearerToken)(agreementId)
      eservice    <- catalogManagementService.getEServiceById(bearerToken)(agreement.eserviceId)
      latestActiveEserviceDescriptor <- eservice.descriptors
        .find(d => d.state == CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
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
        logger.error("Error while updating agreement {}", agreementId, ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, UpdateAgreementError(agreementId))
        upgradeAgreementById400(errorResponse)
    }
  }

}
