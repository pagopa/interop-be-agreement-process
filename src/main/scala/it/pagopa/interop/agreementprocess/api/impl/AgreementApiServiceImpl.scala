package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.model.{Stamp, Stamps, UpdateAgreementSeed}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.api.AgreementApiService
import it.pagopa.interop.agreementprocess.api.impl.ResponseHandlers._
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementprocess.common.readmodel.ReadModelQueries
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules.certifiedAttributesSatisfied
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.model.ClientAgreementAndEServiceDetailsUpdate
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.client.model.{EServiceDescriptor, EServiceDescriptorState}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.certifiedMailSender.model.InteropEnvelope
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.{getOrganizationIdFutureUUID, getUidFutureUUID}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions.{StringOps, _}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  tenantManagementService: TenantManagementService,
  attributeManagementService: AttributeManagementService,
  authorizationManagementService: AuthorizationManagementService,
  partyProcessService: PartyProcessService,
  userRegistry: UserRegistryService,
  readModel: ReadModelService,
  pdfCreator: PDFCreator,
  fileManager: FileManager,
  offsetDateTimeSupplier: OffsetDateTimeSupplier,
  uuidSupplier: UUIDSupplier,
  queueService: QueueService
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val activationMailTemplate: MailTemplate = MailTemplate.activation()

  private val mailDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  val agreementContractCreator: AgreementContractCreator = new AgreementContractCreator(
    pdfCreator,
    fileManager,
    uuidSupplier,
    agreementManagementService,
    attributeManagementService,
    userRegistry,
    offsetDateTimeSupplier
  )

  override def createAgreement(payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      eService       <- catalogManagementService.getEServiceById(payload.eserviceId)
      _              <- CatalogManagementService.validateCreationOnDescriptor(eService, payload.descriptorId)
      _              <- verifyCreationConflictingAgreements(requesterOrgId, payload)
      consumer       <- tenantManagementService.getTenant(requesterOrgId)
      _              <- validateCertifiedAttributes(eService, consumer).whenA(eService.producerId != consumer.id)
      agreement      <- agreementManagementService.createAgreement(payload.toSeed(eService.producerId, requesterOrgId))
    } yield agreement.toApi

    onComplete(result) {
      createAgreementResponse[Agreement](operationLabel)(createAgreement200)
    }
  }

  override def submitAgreement(agreementId: String, payload: AgreementSubmissionPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Submitting agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      _              <- agreement.assertSubmittableState.toFuture
      _              <- verifySubmissionConflictingAgreements(agreement)
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      _              <- CatalogManagementService.validateSubmitOnDescriptor(eService, agreement.descriptorId)
      consumer       <- tenantManagementService.getTenant(agreement.consumerId)
      producer       <- tenantManagementService.getTenant(agreement.producerId)
      updated        <- submit(agreement, eService, consumer, producer, payload)
    } yield updated.toApi

    onComplete(result) {
      submitAgreementResponse[Agreement](operationLabel)(submitAgreement200)
    }
  }

  override def activateAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Activating agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
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
      activateAgreementResponse[Agreement](operationLabel)(activateAgreement200)
    }
  }

  override def suspendAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Suspending agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
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
      suspendAgreementResponse[Agreement](operationLabel)(suspendAgreement200)
    }
  }

  override def upgradeAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Upgrading agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
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
      upgradeAgreementByIdResponse[Agreement](operationLabel)(upgradeAgreementById200)
    }
  }

  override def cloneAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Cloning agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      _              <- Future
        .failed(AgreementNotInExpectedState(agreement.id.toString, agreement.state))
        .unlessA(agreement.state == AgreementManagement.AgreementState.REJECTED)
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      _              <- verifyCloningConflictingAgreements(requesterOrgId, agreement.eserviceId)
      consumer       <- tenantManagementService.getTenant(requesterOrgId)
      _              <- validateCertifiedAttributes(eService, consumer).whenA(eService.producerId != consumer.id)
      newAgreement   <- agreementManagementService.createAgreement(agreement.toSeed)
      documents      <- createAndCopyDocumentsForClonedAgreement(agreement, newAgreement)
      newAgreement   <- Future.successful(newAgreement.copy(consumerDocuments = documents))
    } yield newAgreement.toApi

    onComplete(result) {
      cloneAgreementResponse[Agreement](operationLabel)(cloneAgreement200)
    }
  }

  override def updateAgreementById(agreementId: String, agreementUpdatePayload: AgreementUpdatePayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Updating agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      _              <- agreement.assertUpdateableState.toFuture
      seed = AgreementManagement.UpdateAgreementSeed(
        state = agreement.state,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = agreement.suspendedByConsumer,
        suspendedByProducer = agreement.suspendedByProducer,
        suspendedByPlatform = agreement.suspendedByPlatform,
        consumerNotes = agreementUpdatePayload.consumerNotes.some,
        stamps = agreement.stamps
      )
      updatedAgreement <- agreementManagementService.updateAgreement(agreementUUID, seed)
    } yield updatedAgreement.toApi

    onComplete(result) {
      updateAgreementByIdResponse(operationLabel)(updateAgreementById200)
    }
  }

  override def deleteAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE) {
      val operationLabel = s"Deleting agreement $agreementId"
      logger.info(operationLabel)

      val result: Future[Unit] = for {
        requesterOrgId <- getOrganizationIdFutureUUID(contexts)
        agreementUUID  <- agreementId.toFutureUUID
        agreement      <- agreementManagementService.getAgreementById(agreementUUID)
        _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
        _              <- agreement.assertDeletableState.toFuture
        _              <- Future.traverse(agreement.consumerDocuments)(doc =>
          fileManager.delete(ApplicationConfiguration.storageContainer)(doc.path)
        )
        _              <- agreementManagementService.deleteAgreement(agreement.id)
      } yield ()

      onComplete(result) {
        deleteAgreementResponse[Unit](operationLabel)(_ => deleteAgreement204)
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
    val operationLabel = s"Rejecting agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
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
      rejectAgreementResponse[Agreement](operationLabel)(rejectAgreement200)
    }
  }

  override def getAgreements(
    eservicesIds: String,
    consumersIds: String,
    producersIds: String,
    descriptorsIds: String,
    states: String,
    offset: Int,
    limit: Int,
    showOnlyUpgradeable: Boolean
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Agreements],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel =
      s"Retrieving agreements by EServices $eservicesIds, Consumers $consumersIds, Producers $producersIds, states = $states, showOnlyUpgradeable = $showOnlyUpgradeable"
    logger.info(operationLabel)

    val result: Future[Agreements] = for {
      statesEnum <- parseArrayParameters(states).traverse(AgreementState.fromValue).toFuture
      agreements <- ReadModelQueries.listAgreements(
        parseArrayParameters(eservicesIds),
        parseArrayParameters(consumersIds),
        parseArrayParameters(producersIds),
        parseArrayParameters(descriptorsIds),
        statesEnum,
        showOnlyUpgradeable,
        offset,
        limit
      )(readModel)
      apiAgreements = agreements.results
    } yield Agreements(results = apiAgreements.map(_.toApi), totalCount = agreements.totalCount)

    onComplete(result) { getAgreementsResponse[Agreements](operationLabel)(getAgreements200) }

  }

  override def getAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel = s"Retrieving agreement by id $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      agreementUUID <- agreementId.toFutureUUID
      agreement     <- agreementManagementService.getAgreementById(agreementUUID)
    } yield agreement.toApi

    onComplete(result) {
      getAgreementByIdResponse[Agreement](operationLabel)(getAgreementById200)
    }
  }

  override def computeAgreementsByAttribute(consumerId: String, attributeId: String)(implicit
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, INTERNAL_ROLE, M2M_ROLE) {
    val operationLabel = s"Recalculating agreements status for attribute $attributeId"
    logger.info(operationLabel)

    val allowedStateTransitions: Map[AgreementManagement.AgreementState, List[AgreementManagement.AgreementState]] =
      Map(
        AgreementManagement.AgreementState.DRAFT                        ->
          List(AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES),
        AgreementManagement.AgreementState.PENDING                      ->
          List(AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES),
        AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES ->
          List(AgreementManagement.AgreementState.DRAFT),
        AgreementManagement.AgreementState.ACTIVE    -> List(AgreementManagement.AgreementState.SUSPENDED),
        AgreementManagement.AgreementState.SUSPENDED -> List(
          AgreementManagement.AgreementState.ACTIVE,
          AgreementManagement.AgreementState.SUSPENDED
        )
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
        suspendedByPlatform = newSuspendedByPlatform,
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
          (agreement.state == AgreementManagement.AgreementState.ACTIVE && finalState == AgreementManagement.AgreementState.SUSPENDED) ||
            (agreement.state == AgreementManagement.AgreementState.SUSPENDED && finalState == AgreementManagement.AgreementState.ACTIVE)
        )

      (updateAgreement >> updateClientState)
        .whenA(
          newSuspendedByPlatform != agreement.suspendedByPlatform && allowedStateTransitions
            .get(agreement.state)
            .exists(_.contains(finalState))
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
      agreements    <- agreementManagementService.getAgreements(consumerId = consumerId.some, states = updatableStates)
      consumer      <- tenantManagementService.getTenant(consumerUuid)
      uniqueEServiceIds = agreements.map(_.eserviceId).distinct
      // Not using Future.traverse to not overload our backend. Execution time is not critical for this job
      eServices <- uniqueEServiceIds.traverse(catalogManagementService.getEServiceById)
      filteredEServices = eServices.filter(eServiceContainsAttribute(attributeUuid))
      eServicesMap      = filteredEServices.fproductLeft(_.id).toMap
      filteredAgreement = agreements.filter(a => filteredEServices.exists(_.id == a.eserviceId))
      _ <- filteredAgreement.traverse(updateStates(consumer, eServicesMap))
    } yield ()

    onComplete(result) {
      computeAgreementsByAttributeResponse[Unit](operationLabel)(_ => computeAgreementsByAttribute204)
    }
  }

  def submit(
    agreement: AgreementManagement.Agreement,
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant,
    producer: TenantManagement.Tenant,
    payload: AgreementSubmissionPayload
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, eService, consumer)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              = agreementStateByFlags(nextStateByAttributes, None, None, suspendedByPlatform)

    def calculateStamps(state: AgreementManagement.AgreementState, stamp: Stamp): Future[Stamps] = state match {
      case AgreementManagement.AgreementState.DRAFT                        => Future.successful(agreement.stamps)
      case AgreementManagement.AgreementState.PENDING                      =>
        Future.successful(agreement.stamps.copy(submission = stamp.some))
      case AgreementManagement.AgreementState.ACTIVE                       =>
        Future.successful(agreement.stamps.copy(submission = stamp.some, activation = stamp.some))
      case AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES => Future.successful(agreement.stamps)
      case _ => Future.failed(AgreementNotInExpectedState(agreement.id.toString, newState))
    }

    def validateResultState(state: AgreementManagement.AgreementState) = Future
      .failed(AgreementSubmissionFailed(agreement.id))
      .unlessA(
        List(AgreementManagement.AgreementState.PENDING, AgreementManagement.AgreementState.ACTIVE)
          .contains(state)
      )

    def getUpdateSeed(
      newState: AgreementManagement.AgreementState,
      agreement: AgreementManagement.Agreement,
      stamps: Stamps
    ): UpdateAgreementSeed =
      if (newState == AgreementManagement.AgreementState.ACTIVE)
        AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = matchingCertifiedAttributes(eService, consumer),
          declaredAttributes = matchingDeclaredAttributes(eService, consumer),
          verifiedAttributes = matchingVerifiedAttributes(eService, consumer),
          suspendedByConsumer = agreement.suspendedByConsumer,
          suspendedByProducer = agreement.suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform,
          consumerNotes = payload.consumerNotes,
          stamps = stamps
        )
      else
        AgreementManagement.UpdateAgreementSeed(
          state = newState,
          certifiedAttributes = Nil,
          declaredAttributes = Nil,
          verifiedAttributes = Nil,
          suspendedByConsumer = None,
          suspendedByProducer = None,
          suspendedByPlatform = suspendedByPlatform,
          consumerNotes = payload.consumerNotes,
          stamps = stamps
        )

    def performActivation(agreement: AgreementManagement.Agreement, seed: UpdateAgreementSeed): Future[Unit] = for {
      _ <- agreementContractCreator.create(
        agreement = agreement,
        eService = eService,
        consumer = consumer,
        producer = producer,
        seed = seed
      )
      _ <- sendActivationEnvelope(agreement, producer, consumer, eService)
      _ <- authorizationManagementService
        .updateStateOnClients(
          eServiceId = agreement.eserviceId,
          consumerId = agreement.consumerId,
          agreementId = agreement.id,
          state = toClientState(newState)
        )
    } yield ()

    for {
      uid    <- getUidFutureUUID(contexts)
      stamps <- calculateStamps(newState, Stamp(uid, offsetDateTimeSupplier.get()))
      updateSeed = getUpdateSeed(newState, agreement, stamps)
      updated <- agreementManagementService.updateAgreement(agreement.id, updateSeed)
      _       <- validateResultState(newState)
      _ <- performActivation(updated, updateSeed).whenA(updated.state == AgreementManagement.AgreementState.ACTIVE)
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

    def performActivation(seed: UpdateAgreementSeed, agreement: AgreementManagement.Agreement): Future[Unit] = for {
      producer <- tenantManagementService.getTenant(agreement.producerId)
      _        <- agreementContractCreator.create(
        agreement = agreement,
        eService = eService,
        consumer = consumer,
        producer = producer,
        seed = seed
      )
      _        <- sendActivationEnvelope(agreement, producer, consumer, eService)
    } yield ()

    for {
      seed    <- getUpdateSeed()
      updated <- agreementManagementService.updateAgreement(agreement.id, seed)
      _       <- failOnActivationFailure()
      _       <- performActivation(seed, updated).whenA(firstActivation)
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

  private def sendActivationEnvelope(
    agreement: AgreementManagement.Agreement,
    producerTenant: TenantManagement.Tenant,
    consumerTenant: TenantManagement.Tenant,
    eservice: CatalogManagement.EService
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Unit] = {
    val envelopId: UUID = UUIDSupplier.get()

    def createBody(
      activationDate: OffsetDateTime,
      eserviceName: String,
      eserviceVersion: String,
      producer: String,
      consumer: String
    ): String = activationMailTemplate.body.interpolate(
      Map(
        "activationDate"  -> activationDate.format(mailDateFormat),
        "agreementId"     -> agreement.id.toString,
        "eserviceName"    -> eserviceName,
        "eserviceVersion" -> eserviceVersion,
        "producerName"    -> producer,
        "consumerName"    -> consumer
      )
    )

    val subject: String = activationMailTemplate.subject.interpolate(Map("agreementId" -> agreement.id.toString))

    val envelope: Future[InteropEnvelope] = for {
      producerSelfcareId <- producerTenant.selfcareId.toFuture(SelfcareIdNotFound(producerTenant.id))
      consumerSelfcareId <- consumerTenant.selfcareId.toFuture(SelfcareIdNotFound(consumerTenant.id))
      activationDate     <- agreement.stamps.activation.map(_.when).toFuture(StampNotFound("activation"))
      producer           <- partyProcessService.getInstitution(producerSelfcareId)
      consumer           <- partyProcessService.getInstitution(consumerSelfcareId)
      version            <- eservice.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(eServiceId = eservice.id, descriptorId = agreement.descriptorId))
    } yield InteropEnvelope(
      id = envelopId,
      to = List(producer.digitalAddress, consumer.digitalAddress),
      cc = List.empty,
      bcc = List.empty,
      subject = subject,
      body = createBody(
        activationDate = activationDate,
        producer = producer.description,
        consumer = consumer.description,
        eserviceName = eservice.name,
        eserviceVersion = version.version
      ),
      attachments = List.empty
    )

    envelope.flatMap(queueService.send[InteropEnvelope]).map(_ => ())
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
  ): Seq[AgreementManagement.CertifiedAttribute] = {
    val attributes: Seq[UUID] = consumer.attributes.flatMap(_.certified).filter(_.revocationTimestamp.isEmpty).map(_.id)

    matchingAttributes(eService.attributes.certified, attributes).map(AgreementManagement.CertifiedAttribute)
  }

  def matchingDeclaredAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Seq[AgreementManagement.DeclaredAttribute] = {
    val attributes: Seq[UUID] = consumer.attributes.flatMap(_.declared).filter(_.revocationTimestamp.isEmpty).map(_.id)

    matchingAttributes(eService.attributes.declared, attributes).map(AgreementManagement.DeclaredAttribute)
  }

  def matchingVerifiedAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Seq[AgreementManagement.VerifiedAttribute] = {
    val attributes: Seq[UUID] =
      consumer.attributes.flatMap(_.verified).filter(_.verifiedBy.exists(_.id == eService.producerId)).map(_.id)

    matchingAttributes(eService.attributes.verified, attributes).map(AgreementManagement.VerifiedAttribute)
  }

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

  def verifyCreationConflictingAgreements(consumerId: UUID, payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] = List(
      AgreementManagement.AgreementState.DRAFT,
      AgreementManagement.AgreementState.PENDING,
      AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
      AgreementManagement.AgreementState.ACTIVE,
      AgreementManagement.AgreementState.SUSPENDED
    )
    verifyConflictingAgreements(consumerId, payload.eserviceId, conflictingStates)
  }

  def verifyCloningConflictingAgreements(consumerId: UUID, eserviceId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = {
    val conflictingStates: List[AgreementManagement.AgreementState] = List(
      AgreementManagement.AgreementState.DRAFT,
      AgreementManagement.AgreementState.PENDING,
      AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
      AgreementManagement.AgreementState.ACTIVE,
      AgreementManagement.AgreementState.SUSPENDED
    )
    verifyConflictingAgreements(consumerId, eserviceId, conflictingStates)
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

  def createAndCopyDocumentsForClonedAgreement(
    oldAgreement: AgreementManagement.Agreement,
    newAgreement: AgreementManagement.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Seq[AgreementManagement.Document]] = {
    Future.traverse(oldAgreement.consumerDocuments) { doc =>
      val newDocumentId: UUID     = uuidSupplier.get()
      val newDocumentPath: String =
        s"${ApplicationConfiguration.consumerDocumentsPath}/${newAgreement.id.toString}/${newDocumentId.toString}"

      fileManager
        .copy(ApplicationConfiguration.storageContainer, doc.path)(newDocumentPath, newDocumentId.toString, doc.name)
        .flatMap(storageFilePath =>
          agreementManagementService
            .addConsumerDocument(newAgreement.id, doc.toSeed(newDocumentId, storageFilePath))
        )
    }
  }

  def validateCertifiedAttributes(
    eService: CatalogManagement.EService,
    consumer: TenantManagement.Tenant
  ): Future[Unit] =
    Future
      .failed(MissingCertifiedAttributes(eService.id, consumer.id))
      .unlessA(certifiedAttributesSatisfied(eService, consumer))

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
  )(implicit contexts: Seq[(String, String)]): Future[Unit] =
    verifyConflictingAgreements(agreement.consumerId, agreement.eserviceId, conflictingStates)

  private def verifyConflictingAgreements(
    consumerId: UUID,
    eServiceId: UUID,
    conflictingStates: List[AgreementManagement.AgreementState]
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    for {
      activeAgreement <- agreementManagementService.getAgreements(
        consumerId = Some(consumerId.toString),
        eserviceId = Some(eServiceId.toString),
        states = conflictingStates
      )
      _               <- Future
        .failed(AgreementAlreadyExists(consumerId, eServiceId))
        .whenA(activeAgreement.nonEmpty)
    } yield ()
  }

  def assertCanWorkOnConsumerDocuments(agreementState: AgreementManagement.AgreementState): Future[Unit] =
    Future
      .failed(DocumentsChangeNotAllowed(agreementState))
      .unlessA(
        Set[AgreementManagement.AgreementState](
          AgreementManagement.AgreementState.DRAFT,
          AgreementManagement.AgreementState.PENDING
        ).contains(agreementState)
      )

  override def addAgreementConsumerDocument(agreementId: String, documentSeed: DocumentSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Adding a consumer document to agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Document] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(organizationId, agreement)
      _              <- assertCanWorkOnConsumerDocuments(agreement.state)
      document       <- agreementManagementService.addConsumerDocument(
        agreementUUID,
        AgreementManagement.DocumentSeed(
          id = documentSeed.id,
          name = documentSeed.name,
          prettyName = documentSeed.prettyName,
          contentType = documentSeed.contentType,
          path = documentSeed.path
        )
      )
    } yield document.toApi

    onComplete(result) {
      addAgreementConsumerDocumentResponse[Document](operationLabel)(addAgreementConsumerDocument200)
    }
  }

  override def getAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerDocument: ToEntityMarshaller[Document],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Retrieving consumer document $documentId from agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Document] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumerOrProducer(organizationId, agreement)
      documentUUID   <- documentId.toFutureUUID
      document       <- agreementManagementService.getConsumerDocument(agreementUUID, documentUUID)
    } yield document.toApi

    onComplete(result) {
      getAgreementConsumerDocumentResponse[Document](operationLabel)(getAgreementConsumerDocument200)
    }
  }

  override def removeAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Removing consumer document $documentId from agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Unit] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(organizationId, agreement)
      _              <- assertCanWorkOnConsumerDocuments(agreement.state)
      documentUUID   <- documentId.toFutureUUID
      document       <- agreement.consumerDocuments
        .find(_.id == documentUUID)
        .toFuture(DocumentNotFound(agreementId, documentId))
      result         <- agreementManagementService.removeConsumerDocument(agreementUUID, documentUUID)
      _              <- fileManager.delete(ApplicationConfiguration.storageContainer)(document.path)
    } yield result

    onComplete(result) {
      removeAgreementConsumerDocumentResponse[Unit](operationLabel)(_ => removeAgreementConsumerDocument204)
    }
  }

  override def getAgreementProducers(producerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE) {
    val operationLabel =
      s"Retrieving producers from agreements with producer name $producerName"
    logger.info(operationLabel)

    val result: Future[CompactOrganizations] = for {
      compactTenants <- ReadModelQueries.listProducers(producerName, offset, limit)(readModel)
    } yield CompactOrganizations(results = compactTenants.results, totalCount = compactTenants.totalCount)

    onComplete(result) { getAgreementProducersResponse[CompactOrganizations](operationLabel)(getAgreementProducers200) }
  }

  def getAgreementConsumers(consumerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE) {
    val operationLabel =
      s"Retrieving consumers from agreements with consumer name $consumerName"
    logger.info(operationLabel)

    val result: Future[CompactOrganizations] = for {
      compactTenants <- ReadModelQueries.listConsumers(consumerName, offset, limit)(readModel)
    } yield CompactOrganizations(results = compactTenants.results, totalCount = compactTenants.totalCount)

    onComplete(result) { getAgreementConsumersResponse[CompactOrganizations](operationLabel)(getAgreementConsumers200) }
  }

  def getAgreementEServices(
    eServiceName: Option[String],
    consumersIds: String,
    producersIds: String,
    offset: Int,
    limit: Int
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerCompactEServices: ToEntityMarshaller[CompactEServices],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE) {
    val operationLabel =
      s"Retrieving EServices with consumers $consumersIds, producers $producersIds"
    logger.info(operationLabel)

    val result: Future[CompactEServices] = for {
      eservices <- ReadModelQueries.listEServicesAgreements(
        eServiceName = eServiceName,
        consumersIds = parseArrayParameters(consumersIds),
        producersIds = parseArrayParameters(producersIds),
        offset = offset,
        limit = limit
      )(readModel)
      apiEServices = eservices.results
    } yield CompactEServices(results = apiEServices, totalCount = eservices.totalCount)

    onComplete(result) {
      getAgreementEServicesResponse[CompactEServices](operationLabel)(getAgreementEServices200)
    }
  }
}
