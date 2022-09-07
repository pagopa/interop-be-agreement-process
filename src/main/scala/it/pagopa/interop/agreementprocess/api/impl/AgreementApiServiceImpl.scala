package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
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
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, INTERNAL_ROLE, M2M_ROLE}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getClaimFuture
import it.pagopa.interop.commons.utils.ORGANIZATION_ID_CLAIM
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, StringOps}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

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
      requesterOrgId <- getRequesterOrganizationId(contexts)
      eService       <- catalogManagementService.getEServiceById(payload.eserviceId)
      _              <- CatalogManagementService.validateCreationOnDescriptor(eService, payload.descriptorId)
      _              <- verifyCreationConflictingAgreements(eService.producerId, requesterOrgId, payload)
      consumer       <- tenantManagementService.getTenant(requesterOrgId)
      _              <- validateCertifiedAttributes(eService, consumer)
      agreement      <- agreementManagementService.createAgreement(
        eService.producerId,
        requesterOrgId,
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
        requesterOrgId <- getRequesterOrganizationId(contexts)
        agreement      <- agreementManagementService.getAgreementById(agreementId)
        _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
        _              <- agreement.assertSubmittableState.toFuture
        _              <- verifySubmissionConflictingAgreements(agreement)
        eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
        _              <- CatalogManagementService.validateSubmitOnDescriptor(eService, agreement.descriptorId)
        consumer       <- tenantManagementService.getTenant(agreement.consumerId)
        _              <- validateDeclaredAttributes(eService, consumer)
        updated        <- submit(agreement, eService, consumer)
        _              <- validateCertifiedAttributes(eService, consumer) // Just to return the error. TODO required?
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
        requesterOrgId <- getRequesterOrganizationId(contexts)
        agreement      <- agreementManagementService.getAgreementById(agreementId)
        _              <- verifyConsumerDoesNotActivatePending(agreement, requesterOrgId)
        _              <- assertRequesterIsConsumerOrProducer(requesterOrgId, agreement)
        _              <- agreement.assertActivableState.toFuture
        _              <- verifyActivationConflictingAgreements(agreement)
        eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
        _              <- CatalogManagementService.validateActivationOnDescriptor(eService, agreement.descriptorId)
        consumer       <- tenantManagementService.getTenant(agreement.consumerId)
        updated        <- activate(agreement, eService, consumer, requesterOrgId)
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
        requesterOrgId <- getRequesterOrganizationId(contexts)
        agreement      <- agreementManagementService.getAgreementById(agreementId)
        _              <- assertRequesterIsConsumerOrProducer(requesterOrgId, agreement)
        _              <- agreement.assertSuspendableState.toFuture
        eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
        consumer       <- tenantManagementService.getTenant(agreement.consumerId)
        updated        <- suspend(agreement, eService, consumer, requesterOrgId)
      } yield updated.toApi

      onComplete(result) {
        handleSuspensionError(s"Error while suspending agreement $agreementId") orElse { case Success(agreement) =>
          suspendAgreement200(agreement)
        }
      }
    }

  override def upgradeAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Upgrading agreement $agreementId")
    val result = for {
      requesterOrgId <- getRequesterOrganizationId(contexts)
      agreement      <- agreementManagementService.getAgreementById(agreementId)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      _              <- agreement.assertUpgradableState.toFuture
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      newerVersion   <- CatalogManagementService.getEServiceNewerPublishedVersion(eService, agreement.descriptorId)
      upgradeSeed = AgreementManagement.UpgradeAgreementSeed(descriptorId = newerVersion)
      newAgreement <- agreementManagementService.upgradeById(agreement.id, upgradeSeed)
    } yield newAgreement.toApi

    onComplete(result) {
      handleUpgradeError(s"Error while updating agreement $agreementId") orElse { case Success(agreement) =>
        upgradeAgreementById200(agreement)
      }
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
      filtered    <- latest
        .filter(_ == true)
        .fold(Future.successful(agreements))(_ =>
          AgreementFilter.filterAgreementsByLatestVersion(catalogManagementService, agreements)
        )
    } yield filtered.map(_.toApi)

    onComplete(result) {
      handleListingError(
        s"Error while getting agreements by producer = $producerId, consumer = $consumerId, eservice = $eserviceId, descriptor = $descriptorId, states = $states, latest = $latest"
      ) orElse { case Success(agreement) =>
        getAgreements200(agreement)
      }
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
      handleRetrieveError(s"Error while getting agreement by id $agreementId") orElse { case Success(agreement) =>
        getAgreementById200(agreement)
      }
    }
  }

  override def computeAgreementsByAttribute(consumerId: String, attributeId: String)(implicit
    contexts: Seq[(String, String)]
  ): Route =
    authorize(INTERNAL_ROLE) {
      logger.info(s"Recalculating agreements status for attribute $attributeId")

      def calc(agreement: AgreementManagement.Agreement)(fsmState: AgreementManagement.AgreementState): Future[Unit] = {
        val newSuspendedByPlatform = suspendedByPlatformFlag(fsmState)

        if (newSuspendedByPlatform != agreement.suspendedByPlatform) {
          val finalState = agreementStateByFlags(
            fsmState,
            agreement.suspendedByProducer,
            agreement.suspendedByConsumer,
            newSuspendedByPlatform
          )
          val seed       = AgreementManagement.UpdateAgreementSeed(
            state = finalState,
            certifiedAttributes = agreement.certifiedAttributes,
            declaredAttributes = agreement.declaredAttributes,
            verifiedAttributes = agreement.verifiedAttributes,
            suspendedByConsumer = agreement.suspendedByConsumer,
            suspendedByProducer = agreement.suspendedByProducer,
            suspendedByPlatform = suspendedByPlatformFlag(fsmState),
            consumerNotes = agreement.consumerNotes
          )
          agreementManagementService.updateAgreement(agreement.id, seed).as(())
        } else Future.unit
      }

      def calc2(consumer: TenantManagement.Tenant, eServices: Map[UUID, CatalogManagement.EService])(
        agreement: AgreementManagement.Agreement
      ): Future[Unit] = {
//        val result = for {
//          eService <- eServices.get(agreement.eserviceId)
//        } yield AgreementStateByAttributesFSM.nextState(agreement.state, eService, consumer)
//        result
//          .fold {
//            logger.error(s"Error while recalculating agreements status for attribute $attributeId")
//            Future.unit
//          }(calc(agreement))
        eServices
          .get(agreement.eserviceId)
          .map(AgreementStateByAttributesFSM.nextState(agreement.state, _, consumer))
          .fold {
            logger.error(s"EService ${agreement.eserviceId} not found for agreement ${agreement.id}")
            Future.unit
          }(calc(agreement))
      }

      val result: Future[Unit] = for {
        consumerUuid <- consumerId.toFutureUUID
        agreements   <- agreementManagementService.getAgreements(
          consumerId = consumerId.some,
          attributeId = attributeId.some,
          states = List(
            AgreementManagement.AgreementState.DRAFT,
            AgreementManagement.AgreementState.PENDING,
            AgreementManagement.AgreementState.ACTIVE,
            AgreementManagement.AgreementState.SUSPENDED,
            AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
          )
        )
        uniqueEServiceIds = agreements.map(_.eserviceId).distinct
        consumer  <- tenantManagementService.getTenant(consumerUuid)
        eServices <- Future.traverse(uniqueEServiceIds)(catalogManagementService.getEServiceById)
        eServicesMap = eServices.map(es => es.id -> es).toMap
        _            = agreements.map(calc2(consumer, eServicesMap))
      } yield ()

      onComplete(result) {
        handleComputeAgreementsStateError(
          s"Error while recalculating agreements status for attribute $attributeId"
        ) orElse { case Success(_) =>
          computeAgreementsByAttribute204
        }
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
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
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
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
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
    agreement: AgreementManagement.Agreement,
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

  def getRequesterOrganizationId(contexts: Seq[(String, String)]): Future[UUID] =
    getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)

}
