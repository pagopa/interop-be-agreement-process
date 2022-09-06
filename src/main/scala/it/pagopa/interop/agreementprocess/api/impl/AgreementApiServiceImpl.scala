package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.api.AgreementApiService
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.agreementprocess.error.ErrorHandlers._
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, M2M_ROLE}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getClaimFuture
import it.pagopa.interop.commons.utils.ORGANIZATION_ID_CLAIM
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, OptionOps, StringOps}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  tenantManagementService: TenantManagementService,
  attributeManagementService: AttributeManagementService,
  authorizationManagementService: AuthorizationManagementService
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

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
      _                <- verifyCreationConflictingAgreements(eService.producerId, requesterOrgUuid, payload)
      consumer         <- tenantManagementService.getTenant(requesterOrgUuid)
      _                <- validateCertifiedAttributes(eService, consumer)
      agreement        <- agreementManagementService.createAgreement(
        eService.producerId,
        requesterOrgUuid,
        payload.eserviceId,
        payload.descriptorId
      )
    } yield agreement.toApi

    onComplete(result) {
      handleCreationError(
        s"Error creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
      ) orElse { case Success(agreement) => createAgreement200(agreement) }
    }
  }

  override def submitAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Submitting agreement {}", agreementId)
      val result = for {
        requesterOrgId   <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
        requesterOrgUuid <- requesterOrgId.toFutureUUID
        agreement        <- agreementManagementService.getAgreementById(agreementId)
        _                <- assertRequesterIsConsumer(requesterOrgUuid, agreement)
        _                <- agreement.assertSubmittableState.toFuture
        _                <- verifySubmissionConflictingAgreements(agreement)
        eService         <- catalogManagementService.getEServiceById(agreement.eserviceId)
        _                <- CatalogManagementService.validateSubmitOnDescriptor(eService, agreement.descriptorId)
        consumer         <- tenantManagementService.getTenant(agreement.consumerId)
        _                <- validateDeclaredAttributes(eService, consumer)
        updated          <- submit(agreement, eService, consumer)
        _                <- validateCertifiedAttributes(eService, consumer) // Just to return the error. TODO required?
      } yield updated.toApi

      onComplete(result) {
        handleSubmissionError(s"Error while submitting agreement $agreementId") orElse { case Success(agreement) =>
          submitAgreement200(agreement)
        }
      }
    }

  override def activateAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Activating agreement {}", agreementId)
      val result = for {
        requesterOrgId   <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
        requesterOrgUuid <- requesterOrgId.toFutureUUID
        agreement        <- agreementManagementService.getAgreementById(agreementId)
        _                <- verifyConsumerDoesNotActivatePending(agreement, requesterOrgUuid)
        _                <- assertRequesterIsConsumerOrProducer(requesterOrgUuid, agreement)
        _                <- agreement.assertActivableState.toFuture
        _                <- verifyActivationConflictingAgreements(agreement)
        eService         <- catalogManagementService.getEServiceById(agreement.eserviceId)
        _                <- CatalogManagementService.validateActivationOnDescriptor(eService, agreement.descriptorId)
        consumer         <- tenantManagementService.getTenant(agreement.consumerId)
        updated          <- activate(agreement, eService, consumer, requesterOrgUuid)
      } yield updated.toApi

      onComplete(result) {
        handleActivationError(s"Error while activating agreement $agreementId") orElse { case Success(agreement) =>
          activateAgreement200(agreement)
        }
      }
    }

  override def suspendAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Suspending agreement {}", agreementId)
      val result = for {
        requesterOrgId   <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
        requesterOrgUuid <- requesterOrgId.toFutureUUID
        agreement        <- agreementManagementService.getAgreementById(agreementId)
        _                <- assertRequesterIsConsumerOrProducer(requesterOrgUuid, agreement)
        _                <- agreement.assertSuspendableState.toFuture
        eService         <- catalogManagementService.getEServiceById(agreement.eserviceId)

        consumer <- tenantManagementService.getTenant(agreement.consumerId)
        updated  <- suspend(agreement, eService, consumer, requesterOrgUuid)
      } yield updated.toApi

      onComplete(result) {
        handleSuspensionError(s"Error while suspending agreement $agreementId") orElse { case Success(agreement) =>
          suspendAgreement200(agreement)
        }
      }
    }

  // TODO Test and refactor
  override def upgradeAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Upgrading agreement $agreementId")
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
    } yield newAgreement.toApi

    onComplete(result) {
      case Success(agreement) => upgradeAgreementById200(agreement)
      case Failure(ex)        =>
        logger.error(s"Error while updating agreement $agreementId", ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, UpdateAgreementError(agreementId))
        upgradeAgreementById400(errorResponse)
    }
  }

  def submit(
    agreement: AgreementManagement.Agreement,
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              = agreementStateByFlags(nextStateByAttributes, None, None, suspendedByPlatform)
    val updateSeed            = AgreementManagement.UpdateAgreementSeed(
      state = newState,
      certifiedAttributes = Nil,
      declaredAttributes = Nil,
      verifiedAttributes = Nil,
      suspendedByConsumer = None,
      suspendedByProducer = None,
      suspendedByPlatform = suspendedByPlatform
    )
    agreementManagementService.updateAgreement(agreement.id, updateSeed)
  }

  def activate(
    agreement: AgreementManagement.Agreement,
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant,
    requesterOrgId: UUID
  )(implicit contexts: Seq[(String, String)]): Future[ManagementAgreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
    val suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, AgreementState.ACTIVE)
    val suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, AgreementState.ACTIVE)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              =
      agreementStateByFlags(nextStateByAttributes, suspendedByProducer, suspendedByConsumer, suspendedByPlatform)
    val firstActivation       =
      agreement.state == AgreementManagement.AgreementState.PENDING && newState == AgreementManagement.AgreementState.ACTIVE
    val updateSeed            =
      if (firstActivation)
        AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = matchingCertifiedAttributes(eService, consumer),
          declaredAttributes = matchingDeclaredAttributes(eService, consumer),
          verifiedAttributes = matchingVerifiedAttributes(eService, consumer),
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform
        )
      else
        AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = agreement.certifiedAttributes,
          declaredAttributes = agreement.declaredAttributes,
          verifiedAttributes = agreement.verifiedAttributes,
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform
        )

    val failureStates = List(
      AgreementManagement.AgreementState.DRAFT,
      AgreementManagement.AgreementState.PENDING,
      AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
    )

    def failOnActivationFailure() = Future
      .failed(AgreementActivationFailed(agreement.id))
      .whenA(failureStates.contains(newState))

    for {
      updated <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
      _       <- failOnActivationFailure()
      _       <- authorizationManagementService
        .updateStateOnClients(
          eServiceId = agreement.eserviceId,
          consumerId = agreement.consumerId,
          agreementId = agreement.id,
          state = toClientState(newState)
        )
        .unlessA(failureStates.contains(newState))
    } yield updated
  }

  def suspend(
    agreement: AgreementManagement.Agreement,
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant,
    requesterOrgId: UUID
  )(implicit contexts: Seq[(String, String)]): Future[ManagementAgreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
    val suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, AgreementState.SUSPENDED)
    val suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, AgreementState.SUSPENDED)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              =
      agreementStateByFlags(nextStateByAttributes, suspendedByProducer, suspendedByConsumer, suspendedByPlatform)
    val updateSeed            = AgreementManagement.UpdateAgreementSeed(
      state = newState,
      certifiedAttributes = agreement.certifiedAttributes,
      declaredAttributes = agreement.declaredAttributes,
      verifiedAttributes = agreement.verifiedAttributes,
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      suspendedByPlatform = suspendedByPlatform
    )
    for {
      updated <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
      _       <- authorizationManagementService.updateStateOnClients(
        eServiceId = agreement.eserviceId,
        consumerId = agreement.consumerId,
        agreementId = agreement.id,
        state = AuthorizationManagement.ClientComponentState.INACTIVE
      )
    } yield updated
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
      filtered    <- latest
        .filter(_ == true)
        .fold(Future.successful(agreements))(_ =>
          AgreementFilter.filterAgreementsByLatestVersion(catalogManagementService, agreements)
        )
    } yield filtered.map(_.toApi)

    onComplete(result) {
      case Success(agreement) => getAgreements200(agreement)
      case Failure(ex)        =>
        logger.error(
          s"Error while getting agreements by producer = $producerId, consumer = $consumerId, eservice = $eserviceId, descriptor = $descriptorId, states = $states, latest = $latest",
          ex
        )
        val errorResponse: Problem = problemOf(StatusCodes.BadRequest, RetrieveAgreementsError)
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
      agreement <- agreementManagementService.getAgreementById(agreementId)
    } yield agreement.toApi

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

  def suspendedByConsumerFlag(
    agreement: AgreementManagement.Agreement,
    requesterOrgId: UUID,
    destinationState: AgreementState
  ): Option[Boolean] =
    if (requesterOrgId == agreement.consumerId) Some(destinationState == AgreementState.SUSPENDED)
    else agreement.suspendedByConsumer

  def suspendedByProducerFlag(
    agreement: AgreementManagement.Agreement,
    requesterOrgId: UUID,
    destinationState: AgreementState
  ): Option[Boolean] =
    if (requesterOrgId == agreement.producerId) Some(destinationState == AgreementState.SUSPENDED)
    else agreement.suspendedByProducer

  def suspendedByPlatformFlag(fsmState: AgreementManagement.AgreementState): Option[Boolean] =
    // TODO Which states enable the suspendedByPlatform?
    List(AgreementManagement.AgreementState.SUSPENDED, AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES)
      .contains(fsmState)
      .some

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

  def verifyRequester(requesterId: UUID, expected: UUID): Future[Unit] =
    Future.failed(OperationNotAllowed(requesterId)).whenA(requesterId != expected)

  def assertRequesterIsConsumer(requesterOrgId: UUID, agreement: AgreementManagement.Agreement): Future[Unit] =
    verifyRequester(requesterOrgId, agreement.consumerId)

  def assertRequesterIsProducer(requesterOrgId: UUID, agreement: AgreementManagement.Agreement): Future[Unit] =
    verifyRequester(requesterOrgId, agreement.producerId)

  def assertRequesterIsConsumerOrProducer(
    requesterOrgId: UUID,
    agreement: AgreementManagement.Agreement
  ): Future[Unit] =
    assertRequesterIsConsumer(requesterOrgId, agreement) orElse assertRequesterIsProducer(requesterOrgId, agreement)

  def verifyCreationConflictingAgreements(producerId: UUID, consumerId: UUID, payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] = List(
      AgreementManagement.AgreementState.DRAFT,
      AgreementManagement.AgreementState.PENDING,
      AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
      AgreementManagement.AgreementState.ACTIVE,
      AgreementManagement.AgreementState.SUSPENDED
    )
    verifyConflictingAgreements(producerId, consumerId, payload.eserviceId, payload.descriptorId, conflictingStates)
  }

  def verifySubmissionConflictingAgreements(
    agreement: AgreementManagement.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] = List(
      AgreementManagement.AgreementState.ACTIVE,
      AgreementManagement.AgreementState.SUSPENDED,
      AgreementManagement.AgreementState.PENDING,
      AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
    )
    verifyConflictingAgreements(agreement, conflictingStates)
  }

  def verifyActivationConflictingAgreements(
    agreement: AgreementManagement.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] =
      List(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
    verifyConflictingAgreements(agreement, conflictingStates)
  }

  def validateCertifiedAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Future[Unit] =
    Future
      .failed(MissingCertifiedAttributes(eService.id, consumer.id))
      .unlessA(AgreementStateByAttributesFSM.certifiedAttributesSatisfied(eService, consumer))

  def validateDeclaredAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Future[Unit] =
    Future
      .failed(MissingDeclaredAttributes(eService.id, consumer.id))
      .unlessA(AgreementStateByAttributesFSM.declaredAttributesSatisfied(eService, consumer))

  def verifyConsumerDoesNotActivatePending(
    agreement: AgreementManagement.Agreement,
    requesterOrgUuid: UUID
  ): Future[Unit] =
    Future
      .failed(OperationNotAllowed(requesterOrgUuid))
      .whenA(agreement.state == AgreementManagement.AgreementState.PENDING && agreement.consumerId == requesterOrgUuid)

  def toClientState(state: AgreementManagement.AgreementState): AuthorizationManagement.ClientComponentState =
    state match {
      case AgreementManagement.AgreementState.ACTIVE => AuthorizationManagement.ClientComponentState.ACTIVE
      case _                                         => AuthorizationManagement.ClientComponentState.INACTIVE
    }

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

}
