package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.api.AgreementApiService
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service.AgreementManagementService.agreementStateToApi
import it.pagopa.interop.agreementprocess.service.CatalogManagementService.descriptorStateToApi
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.model.{
  AttributeValue => CatalogAttributeValue,
  EService => CatalogEService
}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, M2M_ROLE}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getClaimFuture
import it.pagopa.interop.commons.utils.ORGANIZATION_ID_CLAIM
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, OptionOps, StringOps}
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Adapters {

  val ACTIVABLE_STATES: Set[AgreementManagement.AgreementState]   =
    Set(AgreementManagement.AgreementState.PENDING, AgreementManagement.AgreementState.SUSPENDED)
  val SUSPENDABLE_STATES: Set[AgreementManagement.AgreementState] =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val ARCHIVABLE_STATES: Set[AgreementManagement.AgreementState]  =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val SUBMITTABLE_STATES: Set[AgreementManagement.AgreementState] = Set(AgreementManagement.AgreementState.DRAFT)

  final case class AgreementNotInExpectedState(agreementId: String, state: AgreementManagement.AgreementState)
      extends ComponentError("0003", s"Agreement $agreementId not in expected state (current state: ${state.toString})")

  implicit class AgreementWrapper(private val a: AgreementManagement.Agreement) extends AnyVal {

    def assertSubmittableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(SUBMITTABLE_STATES.contains(a.state))

    def assertActivableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(ACTIVABLE_STATES.contains(a.state))

    def assertSuspendableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(SUSPENDABLE_STATES.contains(a.state))

    def assertArchivableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(ARCHIVABLE_STATES.contains(a.state))

  }
}

final case class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  tenantManagementService: TenantManagementService,
  attributeManagementService: AttributeManagementService,
  authorizationManagementService: AuthorizationManagementService
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  import Adapters._

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  def suspendedByConsumerFlag(
    agreement: AgreementManagement.Agreement,
    requesterOrgId: String,
    destinationState: AgreementState
  ): Option[Boolean] =
    if (requesterOrgId == agreement.consumerId.toString) Some(destinationState == AgreementState.SUSPENDED)
    else agreement.suspendedByConsumer

  def suspendedByProducerFlag(
    agreement: AgreementManagement.Agreement,
    requesterOrgId: String,
    destinationState: AgreementState
  ): Option[Boolean] =
    if (requesterOrgId == agreement.producerId.toString) Some(destinationState == AgreementState.SUSPENDED)
    else agreement.suspendedByProducer

  def agreementStateByFlags(
    stateByAttribute: AgreementManagement.AgreementState,
    suspendedByProducer: Option[Boolean],
    suspendedByConsumer: Option[Boolean],
    suspendedByPlatform: Option[Boolean]
  ): AgreementManagement.AgreementState =
    (stateByAttribute, suspendedByProducer, suspendedByConsumer, suspendedByPlatform) match {
      case (AgreementManagement.AgreementState.ACTIVE, Some(true), _, _) => AgreementManagement.AgreementState.SUSPENDED
      case (AgreementManagement.AgreementState.ACTIVE, _, Some(true), _) => AgreementManagement.AgreementState.SUSPENDED
      case (AgreementManagement.AgreementState.ACTIVE, _, _, Some(true)) => AgreementManagement.AgreementState.SUSPENDED
      case _                                                             => stateByAttribute
    }

  def matchingAttributes(
    eServiceAttributes: Seq[CatalogManagement.Attribute],
    consumerAttributes: Seq[UUID]
  ): Seq[UUID] =
    // TODO Will compile once Catalog Attribute ID will be fixed
    eServiceAttributes.flatMap(_.single.map(_.id)).intersect(consumerAttributes) ++
      eServiceAttributes.flatMap(_.group).flatten.map(_.id).intersect(consumerAttributes)

  def matchingCertifiedAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Seq[AgreementManagement.CertifiedAttribute] =
    matchingAttributes(eService.attributes.certified, consumer.attributes.flatMap(_.certified).map(_.id))
      .map(AgreementManagement.CertifiedAttribute)

  def matchingDeclaredAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Seq[AgreementManagement.DeclaredAttribute] =
    matchingAttributes(eService.attributes.declared, consumer.attributes.flatMap(_.declared).map(_.id))
      .map(AgreementManagement.DeclaredAttribute)

  def matchingVerifiedAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Seq[AgreementManagement.VerifiedAttribute] =
    matchingAttributes(eService.attributes.verified, consumer.attributes.flatMap(_.verified).map(_.id))
      .map(AgreementManagement.VerifiedAttribute)

  override def createAgreement(payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Creating agreement $payload")
    val result = for {
      requesterOrgId   <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
      requesterOrgUuid <- requesterOrgId.toFutureUUID
      eService         <- catalogManagementService.getEServiceById(payload.eserviceId)
      _                <- CatalogManagementService.validateCreationOnDescriptor(eService, payload.descriptorId)
      _                <- verifyConflictingAgreements(
        eService.producerId,
        requesterOrgUuid,
        payload.eserviceId,
        payload.descriptorId,
        List(
          AgreementManagement.AgreementState.DRAFT,
          AgreementManagement.AgreementState.PENDING,
          AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
          AgreementManagement.AgreementState.ACTIVE,
          AgreementManagement.AgreementState.SUSPENDED
        )
      )
      consumer         <- tenantManagementService.getTenant(requesterOrgUuid)
      _                <- Future
        .failed(MissingCertifiedAttributes(payload.eserviceId, payload.descriptorId, requesterOrgUuid))
        .unlessA(AgreementStateByAttributesFSM.certifiedAttributesSatisfied(eService, consumer))
      agreement        <- agreementManagementService.createAgreement(
        eService.producerId,
        requesterOrgUuid,
        payload.eserviceId,
        payload.descriptorId
      )
      apiAgreement     <- getApiAgreement(agreement) // TODO Still necessary?
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => createAgreement200(agreement)
      case Failure(ex)        =>
        val message =
          s"Error creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
        logger.error(message, ex)
        internalServerError(message)
    }
  }

  override def submitAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Submitting agreement {}", agreementId)
      val result = for {
        agreement <- agreementManagementService.getAgreementById(agreementId)
        _         <- verifyConflictingAgreements(
          agreement,
          List(
            AgreementManagement.AgreementState.ACTIVE,
            AgreementManagement.AgreementState.SUSPENDED,
            AgreementManagement.AgreementState.PENDING,
            AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
          )
        )
        eService  <- catalogManagementService.getEServiceById(agreement.eserviceId)
        // TODO Check which descriptor states are allowed
        _         <- CatalogManagementService.validateSubmitOnDescriptor(eService, agreement.descriptorId)
        _         <- agreement.assertSubmittableState.toFuture

        consumer <- tenantManagementService.getTenant(agreement.consumerId)
        nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
        suspendedByPlatform   = Some(
          nextStateByAttributes != AgreementManagement.AgreementState.ACTIVE
        ) // TODO Which states enable the suspendedByPlatform?
        newState              = agreementStateByFlags(nextStateByAttributes, None, None, suspendedByPlatform)
        updateSeed            = AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = Nil,
          declaredAttributes = Nil,
          verifiedAttributes = Nil,
          suspendedByConsumer = None,
          suspendedByProducer = None,
          suspendedByPlatform = suspendedByPlatform
        )
        _ <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
      } yield ()

      onComplete(result) {
        case Success(_)  => activateAgreement204
        case Failure(ex) =>
          logger.error(s"Error while activating agreement $agreementId", ex)
          activateAgreement400(problemOf(StatusCodes.BadRequest, ActivateAgreementError(agreementId)))
      }
    }

  override def activateAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Activating agreement {}", agreementId)
      val result = for {
        requesterOrgId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
        agreement      <- agreementManagementService.getAgreementById(agreementId)
        _              <- verifyConflictingAgreements(
          agreement,
          List(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
        )
        eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
        _              <- CatalogManagementService.validateActivationOnDescriptor(eService, agreement.descriptorId)
        _              <- agreement.assertActivableState.toFuture

        consumer <- tenantManagementService.getTenant(agreement.consumerId)
        nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
        suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, AgreementState.ACTIVE)
        suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, AgreementState.ACTIVE)
        suspendedByPlatform   = Some(
          nextStateByAttributes != AgreementManagement.AgreementState.ACTIVE
        ) // TODO Which states enable the suspendedByPlatform?
        newState              = agreementStateByFlags(
          nextStateByAttributes,
          suspendedByProducer,
          suspendedByConsumer,
          suspendedByPlatform
        )
        updateSeed            = AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = matchingCertifiedAttributes(eService, consumer),
          declaredAttributes = matchingDeclaredAttributes(eService, consumer),
          verifiedAttributes = matchingVerifiedAttributes(eService, consumer),
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform
        )
        _ <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
        _ <- authorizationManagementService.updateStateOnClients(
          eServiceId = agreement.eserviceId,
          consumerId = agreement.consumerId,
          agreementId = agreement.id,
          state =
            if (newState == AgreementManagement.AgreementState.ACTIVE)
              AuthorizationManagement.ClientComponentState.ACTIVE
            else AuthorizationManagement.ClientComponentState.INACTIVE
        )
      } yield ()

      onComplete(result) {
        case Success(_)  => activateAgreement204
        case Failure(ex) =>
          logger.error(s"Error while activating agreement $agreementId", ex)
          activateAgreement400(problemOf(StatusCodes.BadRequest, ActivateAgreementError(agreementId)))
      }
    }

  override def suspendAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Suspending agreement {}", agreementId)
      val result = for {
        requesterOrgId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
        agreement      <- agreementManagementService.getAgreementById(agreementId)
        eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
        _              <- agreement.assertSuspendableState.toFuture

        consumer <- tenantManagementService.getTenant(agreement.consumerId)
        nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
        suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, AgreementState.SUSPENDED)
        suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, AgreementState.SUSPENDED)
        suspendedByPlatform   = Some(
          nextStateByAttributes != AgreementManagement.AgreementState.ACTIVE
        ) // TODO Which states enable the suspendedByPlatform?
        newState              = agreementStateByFlags(
          nextStateByAttributes,
          suspendedByProducer,
          suspendedByConsumer,
          suspendedByPlatform
        )
        updateSeed            = AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = agreement.certifiedAttributes,
          declaredAttributes = agreement.declaredAttributes,
          verifiedAttributes = agreement.verifiedAttributes,
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform
        )
        _ <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
        _ <- authorizationManagementService.updateStateOnClients(
          eServiceId = agreement.eserviceId,
          consumerId = agreement.consumerId,
          agreementId = agreement.id,
          state = AuthorizationManagement.ClientComponentState.INACTIVE
        )
      } yield ()

      onComplete(result) {
        case Success(_)  => suspendAgreement204
        case Failure(ex) =>
          logger.error(s"Error while suspending agreement $agreementId", ex)
          val errorResponse: Problem =
            problemOf(StatusCodes.BadRequest, SuspendAgreementError(agreementId))
          suspendAgreement400(errorResponse)
      }
    }

  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    states: String,
    latest: Option[Boolean]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, M2M_ROLE) {
    logger.info(
      s"Getting agreements by producer = $producerId, consumer = $consumerId, eservice = $eserviceId, descriptor = $descriptorId, states = $states, latest = $latest"
    )
    val result: Future[Seq[Agreement]] = for {
      statesEnums <- parseArrayParameters(states).traverse(AgreementManagement.AgreementState.fromValue).toFuture
      agreements  <- agreementManagementService.getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        states = statesEnums
      )
      apiAgreementsWithVersion <- Future.traverse(agreements)(getApiAgreement)
      apiAgreements            <- AgreementFilter.filterAgreementsByLatestVersion(latest, apiAgreementsWithVersion)
    } yield apiAgreements

    onComplete(result) {
      case Success(agreement) => getAgreements200(agreement)
      case Failure(ex)        =>
        logger.error(
          s"Error while getting agreements by producer = $producerId, consumer = $consumerId, eservice = $eserviceId, descriptor = $descriptorId, states = $states, latest = $latest",
          ex
        )
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, RetrieveAgreementsError)
        getAgreements400(errorResponse)
    }
  }

  override def getAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE, M2M_ROLE) {
    logger.info(s"Getting agreement by id $agreementId")
    val result: Future[Agreement] = for {
      agreement    <- agreementManagementService.getAgreementById(agreementId)
      apiAgreement <- getApiAgreement(agreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => getAgreementById200(agreement)
      case Failure(ex)        =>
        logger.error(s"Error while getting agreement by id $agreementId", ex)
        ex match {
          case ex: AgreementNotFound =>
            val errorResponse: Problem =
              problemOf(StatusCodes.NotFound, ex)
            getAgreementById404(errorResponse)
          case _                     =>
            val errorResponse: Problem =
              problemOf(StatusCodes.BadRequest, RetrieveAgreementError(agreementId))
            getAgreementById400(errorResponse)
        }
    }
  }

  private def getApiAgreement(
    agreement: ManagementAgreement
  )(implicit contexts: Seq[(String, String)]): Future[Agreement] = {
    for {
      eservice               <- catalogManagementService.getEServiceById(agreement.eserviceId)
      descriptor             <- eservice.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(agreement.eserviceId.toString, agreement.descriptorId.toString))
      activeDescriptorOption <- CatalogManagementService.getActiveDescriptorOption(eservice, descriptor)
      producer               <- partyManagementService.getInstitution(agreement.producerId)
      consumer               <- partyManagementService.getInstitution(agreement.consumerId)
      attributes             <- getApiAgreementAttributes(agreement.verifiedAttributes, eservice)
    } yield Agreement(
      id = agreement.id,
      producer = Organization(id = producer.originId, name = producer.description),
      consumer = Organization(id = consumer.originId, name = consumer.description),
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
    verifiedAttributes: Seq[AgreementManagement.VerifiedAttribute],
    eService: CatalogEService
  )(implicit contexts: Seq[(String, String)]): Future[Seq[AgreementAttributes]] =
    for {
      // TODO Disabled for development
//      attributes <- Future.traverse(verifiedAttributes)(getApiAttribute(eService.attributes))
      attributes <- Future.successful[Seq[Attribute]](Nil)
      _                        = verifiedAttributes
      _                        = contexts
      // TODO end
      eServiceSingleAttributes = eService.attributes.verified.flatMap(_.single)
      eServiceGroupAttributes  = eService.attributes.verified.flatMap(_.group)
      // This traverse over Future is ok since eServiceToAgreementAttribute is sync
      agreementSingleAttributes <- eServiceSingleAttributes.traverse(eServiceToAgreementAttribute(_, attributes))
      agreementGroupAttributes  <- eServiceGroupAttributes.traverse(
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
      .find(_.id == eServiceAttributeValue.id)
      .toFuture(AgreementAttributeNotFound(eServiceAttributeValue.id.toString))

//  private def getApiAttribute(attributes: ManagementAttributes)(verifiedAttribute: VerifiedAttribute)(implicit
//    contexts: Seq[(String, String)]
//  ): Future[Attribute] =
//  {
//    val fromSingle: Seq[CatalogAttributeValue] =
//      attributes.verified.flatMap(attribute => attribute.single.toList.find(_.id == verifiedAttribute.id))
//
//    val fromGroup: Seq[CatalogAttributeValue] =
//      attributes.verified.flatMap(attribute => attribute.group.flatMap(_.find(_.id == verifiedAttribute.id)))
//
//    val allVerifiedAttributes: Map[String, Boolean] =
//      (fromSingle ++ fromGroup).map(attribute => attribute.id -> attribute.explicitAttributeVerification).toMap
//
//    for {
//      att  <- attributeManagementService.getAttribute(verifiedAttribute.id.toString)
//      uuid <- att.id.toFutureUUID
//    } yield Attribute(
//      id = uuid,
//      code = att.code,
//      description = att.description,
//      origin = att.origin,
//      name = att.name,
//      explicitAttributeVerification = allVerifiedAttributes.get(att.id),
//      verified = verifiedAttribute.verified,
//      verificationDate = verifiedAttribute.verificationDate,
//      validityTimespan = verifiedAttribute.validityTimespan
//    )
//
//  }

  private def verifyConflictingAgreements(
    agreement: ManagementAgreement,
    conflictingStates: List[AgreementManagement.AgreementState]
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = verifyConflictingAgreements(
    agreement.producerId,
    agreement.consumerId,
    agreement.eserviceId,
    agreement.descriptorId,
    conflictingStates
  )

  private def verifyConflictingAgreements(
    producerId: UUID,
    consumerId: UUID,
    eServiceId: UUID,
    descriptorId: UUID,
    conflictingStates: List[AgreementManagement.AgreementState]
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    for {
      activeAgreement <- agreementManagementService.getAgreements(
        producerId = Some(producerId.toString),
        consumerId = Some(consumerId.toString),
        eserviceId = Some(eServiceId.toString),
        descriptorId = Some(descriptorId.toString),
        states = conflictingStates
      )
      _               <- Future
        .failed(AgreementAlreadyExists(producerId, consumerId, eServiceId, descriptorId))
        .whenA(activeAgreement.nonEmpty)
    } yield ()
  }

  override def upgradeAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Updating agreement $agreementId")
    val result = for {
      agreement                      <- agreementManagementService.getAgreementById(agreementId)
      eservice                       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      latestActiveEserviceDescriptor <- eservice.descriptors
        .find(d => d.state == CatalogManagement.EServiceDescriptorState.PUBLISHED)
        .toFuture(DescriptorNotFound(agreement.eserviceId.toString, agreement.descriptorId.toString))
      latestDescriptorVersion = latestActiveEserviceDescriptor.version.toLongOption
      currentVersion = eservice.descriptors.find(d => d.id == agreement.descriptorId).flatMap(_.version.toLongOption)
      _ <- CatalogManagementService.hasEserviceNewPublishedVersion(latestDescriptorVersion, currentVersion)
      upgradeSeed = AgreementManagement.UpgradeAgreementSeed(descriptorId = latestActiveEserviceDescriptor.id)
      newAgreement <- agreementManagementService.upgradeById(agreement.id, upgradeSeed)
      apiAgreement <- getApiAgreement(newAgreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => upgradeAgreementById200(agreement)
      case Failure(ex)        =>
        logger.error(s"Error while updating agreement $agreementId", ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, UpdateAgreementError(agreementId))
        upgradeAgreementById400(errorResponse)
    }
  }

  private def internalServerError(errorMessage: String): StandardRoute =
    complete(StatusCodes.InternalServerError, UnexpectedError(errorMessage))
}
