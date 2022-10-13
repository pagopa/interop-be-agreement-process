package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.model.{AgreementState, Stamp, Stamps, UpdateAgreementSeed}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.api.AgreementApiService
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.agreementprocess.error.ErrorHandlers._
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.model.ClientAgreementAndEServiceDetailsUpdate
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.model.{EServiceDescriptor, EServiceDescriptorState}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, INTERNAL_ROLE, M2M_ROLE}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.{getOrganizationIdFutureUUID, getUidFutureUUID}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, StringOps}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  tenantManagementService: TenantManagementService,
  attributeManagementService: AttributeManagementService,
  authorizationManagementService: AuthorizationManagementService,
  partyManagementService: PartyManagementService,
  userRegistry: UserRegistryService,
  pdfCreator: PDFCreator,
  fileManager: FileManager,
  offsetDateTimeSupplier: OffsetDateTimeSupplier,
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  val agreementContractCreator: AgreementContractCreator = new AgreementContractCreator(
    pdfCreator,
    fileManager,
    uuidSupplier,
    agreementManagementService,
    attributeManagementService,
    partyManagementService,
    userRegistry,
    offsetDateTimeSupplier
  )

  override def createAgreement(payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Creating agreement $payload")
    val result = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
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
        requesterOrgId <- getOrganizationIdFutureUUID(contexts)
        agreementUUID  <- agreementId.toFutureUUID
        agreement      <- agreementManagementService.getAgreementById(agreementUUID)
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
        requesterOrgId <- getOrganizationIdFutureUUID(contexts)
        agreementUUID  <- agreementId.toFutureUUID
        agreement      <- agreementManagementService.getAgreementById(agreementUUID)
        _              <- assertRequesterIsConsumerOrProducer(requesterOrgId, agreement)
        _              <- verifyConsumerDoesNotActivatePending(agreement, requesterOrgId)
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
        requesterOrgId <- getOrganizationIdFutureUUID(contexts)
        agreementUUID  <- agreementId.toFutureUUID
        agreement      <- agreementManagementService.getAgreementById(agreementUUID)
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
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      _              <- agreement.assertUpgradableState.toFuture
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      newDescriptor  <- CatalogManagementService.getEServiceNewerPublishedDescriptor(eService, agreement.descriptorId)
      uid            <- getUidFutureUUID(contexts)
      stamp       = Stamp(uid, offsetDateTimeSupplier.get())
      upgradeSeed = AgreementManagement.UpgradeAgreementSeed(descriptorId = newDescriptor.id, stamp)
      newAgreement <- agreementManagementService.upgradeById(agreement.id, upgradeSeed)
      payload = getClientUpgradePayload(newAgreement, newDescriptor)
      _ <- authorizationManagementService.updateAgreementAndEServiceStates(
        newAgreement.eserviceId,
        newAgreement.consumerId,
        payload
      )
    } yield newAgreement.toApi

    onComplete(result) {
      handleUpgradeError(s"Error while updating agreement $agreementId") orElse { case Success(agreement) =>
        upgradeAgreementById200(agreement)
      }
    }
  }

  override def deleteAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE) {
      logger.info(s"Deleting agreement $agreementId")
      val result = for {
        requesterOrgId <- getOrganizationIdFutureUUID(contexts)
        agreementUUID  <- agreementId.toFutureUUID
        agreement      <- agreementManagementService.getAgreementById(agreementUUID)
        _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
        _              <- agreement.assertDeletableState.toFuture
        _              <- Future.traverse(agreement.consumerDocuments)(doc =>
          fileManager.delete(ApplicationConfiguration.consumerDocumentsPath)(doc.path)
        )
        _              <- agreementManagementService.deleteAgreement(agreement.id)
      } yield ()

      onComplete(result) {
        handleDeletionError(s"Error while deleting agreement $agreementId") orElse { case Success(_) =>
          deleteAgreement204
        }
      }
    }

  def getClientUpgradePayload(
    newAgreement: AgreementManagement.Agreement,
    newDescriptor: EServiceDescriptor
  ): ClientAgreementAndEServiceDetailsUpdate = {
    ClientAgreementAndEServiceDetailsUpdate(
      agreementId = newAgreement.id,
      agreementState = toClientState(newAgreement.state),
      descriptorId = newDescriptor.id,
      audience = newDescriptor.audience,
      voucherLifespan = newDescriptor.voucherLifespan,
      eserviceState = toClientState(newDescriptor.state)
    )
  }

  override def rejectAgreement(agreementId: String, payload: AgreementRejectionPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Rejecting agreement $agreementId")
    val result = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsProducer(requesterOrgId, agreement)
      _              <- agreement.assertRejectableState.toFuture
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      consumer       <- tenantManagementService.getTenant(agreement.consumerId)
      uid            <- getUidFutureUUID(contexts)
      updated        <- reject(agreement, eService, consumer, payload, Stamp(uid, offsetDateTimeSupplier.get()))
    } yield updated.toApi

    onComplete(result) {
      handleRejectionError(s"Error while rejecting agreement $agreementId") orElse { case Success(agreement) =>
        rejectAgreement200(agreement)
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
        .filter(identity)
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
      agreementUUID <- agreementId.toFutureUUID
      agreement     <- agreementManagementService.getAgreementById(agreementUUID)
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

      val allowedStateTransitions: Map[AgreementManagement.AgreementState, AgreementManagement.AgreementState] =
        Map(
          AgreementManagement.AgreementState.DRAFT                        ->
            AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
          AgreementManagement.AgreementState.PENDING                      ->
            AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
          AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES ->
            AgreementManagement.AgreementState.DRAFT,
          AgreementManagement.AgreementState.ACTIVE    -> AgreementManagement.AgreementState.SUSPENDED,
          AgreementManagement.AgreementState.SUSPENDED -> AgreementManagement.AgreementState.ACTIVE
        )

      def updateAgreement(
        agreement: AgreementManagement.Agreement
      )(fsmState: AgreementManagement.AgreementState): Future[Unit] = {
        val newSuspendedByPlatform = suspendedByPlatformFlag(fsmState)

        val finalState = agreementStateByFlags(
          fsmState,
          agreement.suspendedByProducer,
          agreement.suspendedByConsumer,
          newSuspendedByPlatform
        )

        val seed = AgreementManagement.UpdateAgreementSeed(
          state = finalState,
          certifiedAttributes = agreement.certifiedAttributes,
          declaredAttributes = agreement.declaredAttributes,
          verifiedAttributes = agreement.verifiedAttributes,
          suspendedByConsumer = agreement.suspendedByConsumer,
          suspendedByProducer = agreement.suspendedByProducer,
          suspendedByPlatform = suspendedByPlatformFlag(fsmState),
          consumerNotes = agreement.consumerNotes,
          stamps = agreement.stamps
        )

        lazy val updateAgreement   = agreementManagementService.updateAgreement(agreement.id, seed)
        lazy val updateClientState = authorizationManagementService
          .updateStateOnClients(
            eServiceId = agreement.eserviceId,
            consumerId = agreement.consumerId,
            agreementId = agreement.id,
            state = toClientState(finalState)
          )
          .whenA(
            List(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
              .contains(finalState)
          )

        (updateAgreement >> updateClientState)
          .whenA(
            newSuspendedByPlatform != agreement.suspendedByPlatform && allowedStateTransitions
              .get(agreement.state)
              .contains(finalState)
          )

      }

      def updateStates(consumer: TenantManagement.Tenant, eServices: Map[UUID, CatalogManagement.EService])(
        agreement: AgreementManagement.Agreement
      ): Future[Unit] =
        eServices
          .get(agreement.eserviceId)
          .map(AgreementStateByAttributesFSM.nextState(agreement, _, consumer))
          .fold {
            logger.error(s"EService ${agreement.eserviceId} not found for agreement ${agreement.id}")
            Future.unit
          }(updateAgreement(agreement))

      val updatableStates = allowedStateTransitions.map { case (startingState, _) => startingState }.toList

      def eServiceContainsAttribute(attributeId: UUID)(eService: CatalogManagement.EService): Boolean =
        (eService.attributes.certified ++ eService.attributes.declared ++ eService.attributes.verified)
          .flatMap(attr => attr.single.map(_.id).toSeq ++ attr.group.traverse(_.map(_.id)).flatten)
          .contains(attributeId)

      val result: Future[Unit] = for {
        consumerUuid  <- consumerId.toFutureUUID
        attributeUuid <- attributeId.toFutureUUID
        agreements <- agreementManagementService.getAgreements(consumerId = consumerId.some, states = updatableStates)
        consumer   <- tenantManagementService.getTenant(consumerUuid)
        uniqueEServiceIds = agreements.map(_.eserviceId).distinct
        // Not using Future.traverse to not overload our backend. Execution time is not critical for this job
        eServices <- uniqueEServiceIds.traverse(catalogManagementService.getEServiceById)
        filteredEServices = eServices.filter(eServiceContainsAttribute(attributeUuid))
        eServicesMap      = filteredEServices.fproductLeft(_.id).toMap
        filteredAgreement = agreements.filter(a => filteredEServices.exists(_.id == a.eserviceId))
        _ <- filteredAgreement.traverse(updateStates(consumer, eServicesMap))
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
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, eService, consumer)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              = agreementStateByFlags(nextStateByAttributes, None, None, suspendedByPlatform)

    def calculateStamps(state: AgreementState, stamp: Stamp): Future[Stamps] = state match {
      case AgreementState.DRAFT   => Future.successful(agreement.stamps)
      case AgreementState.PENDING => Future.successful(agreement.stamps.copy(submission = stamp.some))
      case AgreementState.ACTIVE  =>
        Future.successful(agreement.stamps.copy(submission = stamp.some, activation = stamp.some))
      case AgreementState.MISSING_CERTIFIED_ATTRIBUTES => Future.successful(agreement.stamps)
      case _ => Future.failed(AgreementNotInExpectedState(agreement.id.toString(), newState))
    }

    for {
      uid    <- getUidFutureUUID(contexts)
      stamps <- calculateStamps(newState, Stamp(uid, offsetDateTimeSupplier.get()))
      updateSeed = AgreementManagement.UpdateAgreementSeed(
        state = newState,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = suspendedByPlatform,
        stamps = stamps
      )
      updated <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
    } yield updated

  }

  def activate(
    agreement: AgreementManagement.Agreement,
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant,
    requesterOrgId: UUID
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, eService, consumer)
    val suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, AgreementState.ACTIVE)
    val suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, AgreementState.ACTIVE)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              =
      agreementStateByFlags(nextStateByAttributes, suspendedByProducer, suspendedByConsumer, suspendedByPlatform)
    val firstActivation       =
      agreement.state == AgreementManagement.AgreementState.PENDING && newState == AgreementManagement.AgreementState.ACTIVE

    def getUpdateSeed(): Future[UpdateAgreementSeed] = getUidFutureUUID(contexts).map { uid =>
      val stamp = Stamp(uid, offsetDateTimeSupplier.get()).some

      if (firstActivation)
        AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = matchingCertifiedAttributes(eService, consumer),
          declaredAttributes = matchingDeclaredAttributes(eService, consumer),
          verifiedAttributes = matchingVerifiedAttributes(eService, consumer),
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform,
          stamps = agreement.stamps.copy(activation = stamp)
        )
      else
        AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = agreement.certifiedAttributes,
          declaredAttributes = agreement.declaredAttributes,
          verifiedAttributes = agreement.verifiedAttributes,
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform,
          stamps = agreement.stamps.copy(suspension = None)
        )
    }

    val failureStates = List(
      AgreementManagement.AgreementState.DRAFT,
      AgreementManagement.AgreementState.PENDING,
      AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
    )

    def failOnActivationFailure() = Future
      .failed(AgreementActivationFailed(agreement.id))
      .whenA(failureStates.contains(newState))

    for {
      seed    <- getUpdateSeed()
      _       <-
        if (firstActivation) agreementContractCreator.create(agreement, eService, consumer, seed)
        else Future.unit
      updated <- agreementManagementService.updateAgreement(agreement.id, seed)
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
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, eService, consumer)
    val suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, AgreementState.SUSPENDED)
    val suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, AgreementState.SUSPENDED)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              =
      agreementStateByFlags(nextStateByAttributes, suspendedByProducer, suspendedByConsumer, suspendedByPlatform)

    for {
      uid <- getUidFutureUUID(contexts)
      stamp      = Stamp(uid, offsetDateTimeSupplier.get()).some
      updateSeed = AgreementManagement.UpdateAgreementSeed(
        state = newState,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = suspendedByConsumer,
        suspendedByProducer = suspendedByProducer,
        suspendedByPlatform = suspendedByPlatform,
        stamps = agreement.stamps.copy(suspension = stamp)
      )
      updated <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
      _       <- authorizationManagementService.updateStateOnClients(
        eServiceId = agreement.eserviceId,
        consumerId = agreement.consumerId,
        agreementId = agreement.id,
        state = AuthorizationManagement.ClientComponentState.INACTIVE
      )
    } yield updated
  }

  def reject(
    agreement: AgreementManagement.Agreement,
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant,
    payload: AgreementRejectionPayload,
    stamp: Stamp
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val updateSeed = AgreementManagement.UpdateAgreementSeed(
      state = AgreementManagement.AgreementState.REJECTED,
      certifiedAttributes = matchingCertifiedAttributes(eService, consumer),
      declaredAttributes = matchingDeclaredAttributes(eService, consumer),
      verifiedAttributes = matchingVerifiedAttributes(eService, consumer),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      suspendedByPlatform = None,
      rejectionReason = payload.reason.some,
      stamps = agreement.stamps.copy(rejection = stamp.some)
    )
    agreementManagementService.updateAgreement(agreement.id, updateSeed)
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
  )(implicit contexts: Seq[(String, String)]): Future[Unit] =
    verifyFirstActivationConflictingAgreements(agreement)
      .whenA(agreement.state == AgreementManagement.AgreementState.PENDING) >>
      verifyReActivationConflictingAgreements(agreement)
        .whenA(agreement.state != AgreementManagement.AgreementState.PENDING)

  def verifyFirstActivationConflictingAgreements(
    agreement: AgreementManagement.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] =
      List(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
    verifyConflictingAgreements(agreement, conflictingStates)
  }

  def verifyReActivationConflictingAgreements(
    agreement: AgreementManagement.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] =
      List(AgreementManagement.AgreementState.ACTIVE)
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
      .whenA(
        agreement.state == AgreementManagement.AgreementState.PENDING &&
          agreement.consumerId == requesterOrgUuid &&
          agreement.producerId != agreement.consumerId
      )

  def toClientState(state: AgreementManagement.AgreementState): AuthorizationManagement.ClientComponentState =
    state match {
      case AgreementManagement.AgreementState.ACTIVE => AuthorizationManagement.ClientComponentState.ACTIVE
      case _                                         => AuthorizationManagement.ClientComponentState.INACTIVE
    }

  def toClientState(state: EServiceDescriptorState): AuthorizationManagement.ClientComponentState =
    state match {
      case EServiceDescriptorState.PUBLISHED | EServiceDescriptorState.DEPRECATED =>
        AuthorizationManagement.ClientComponentState.ACTIVE
      case _ => AuthorizationManagement.ClientComponentState.INACTIVE
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

  override def addAgreementConsumerDocument(agreementId: String, documentSeed: DocumentSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {

    logger.info(s"Adding a consumer document to agreement $agreementId")

    val result: Future[Document] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(organizationId, agreement)
      document       <- agreementManagementService.addConsumerDocument(
        agreementUUID,
        AgreementManagement.DocumentSeed(
          name = documentSeed.name,
          prettyName = documentSeed.prettyName,
          contentType = documentSeed.contentType,
          path = documentSeed.path
        )
      )
    } yield document.toApi

    onComplete(result) {
      handleAddDocumentError(s"Error adding a consumer document to agreement $agreementId") orElse {
        case Success(document) =>
          addAgreementConsumerDocument200(document)
      }
    }
  }

  override def getAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {

    logger.info(s"Getting consumer document $documentId from agreement $agreementId")

    val result: Future[Document] = for {
      agreementUUID <- agreementId.toFutureUUID
      documentUUID  <- documentId.toFutureUUID
      document      <- agreementManagementService.getConsumerDocument(agreementUUID, documentUUID)
    } yield document.toApi

    onComplete(result) {
      handleGetDocumentError(s"Error getting consumer document $documentId from agreement $agreementId") orElse {
        case Success(document) =>
          getAgreementConsumerDocument200(document)
      }
    }
  }

  override def removeAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {

    logger.info(s"Removing consumer document $documentId from agreement $agreementId")

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(organizationId, agreement)
      documentUUID   <- documentId.toFutureUUID
      result         <- agreementManagementService.removeConsumerDocument(agreementUUID, documentUUID)
    } yield result

    onComplete(result) {
      handleGetDocumentError(s"Error removing consumer document $documentId from agreement $agreementId") orElse {
        case Success(_) => removeAgreementConsumerDocument204
      }
    }
  }

}
