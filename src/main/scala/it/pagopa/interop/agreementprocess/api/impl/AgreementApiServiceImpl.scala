package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementmanagement.model.agreement.{MissingCertifiedAttributes, _}
import it.pagopa.interop.agreementprocess.api.AgreementApiService
import it.pagopa.interop.agreementprocess.api.impl.ResponseHandlers._
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementprocess.common.readmodel.ReadModelAgreementQueries
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{
  MissingCertifiedAttributes => MissingCertifiedAttributesError,
  _
}
import it.pagopa.interop.agreementprocess.events.ArchiveEvent
import it.pagopa.interop.agreementprocess.events.Events._
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.model.ClientAgreementAndEServiceDetailsUpdate
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.catalogmanagement.model.{
  CatalogAttribute,
  CatalogDescriptor,
  CatalogDescriptorState,
  CatalogItem,
  Deprecated,
  Published
}
import it.pagopa.interop.commons.mail.{Mail, TextMail}
import it.pagopa.interop.commons.mail.Mail._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.{getOrganizationIdFutureUUID, getUidFutureUUID}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentTenant,
  PersistentTenantAttribute,
  PersistentVerifiedAttribute
}
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenantMailKind

final case class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  tenantManagementService: TenantManagementService,
  attributeManagementService: AttributeManagementService,
  authorizationManagementService: AuthorizationManagementService,
  partyProcessService: PartyProcessService,
  userRegistry: UserRegistryService,
  pdfCreator: PDFCreator,
  fileManager: FileManager,
  offsetDateTimeSupplier: OffsetDateTimeSupplier,
  uuidSupplier: UUIDSupplier,
  certifiedMailQueueService: QueueService,
  archivingPurposesQueueService: QueueService,
  archivingEservicesQueueService: QueueService
)(implicit ec: ExecutionContext, readModel: ReadModelService)
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
      descriptor     <- CatalogManagementService.validateCreationOnDescriptor(eService, payload.descriptorId)
      _              <- verifyCreationConflictingAgreements(requesterOrgId, payload)
      consumer       <- tenantManagementService
        .getTenantById(requesterOrgId)
      _              <- validateCertifiedAttributes(descriptor, consumer).whenA(eService.producerId != consumer.id)
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
      eService       <- catalogManagementService
        .getEServiceById(agreement.eserviceId)
      descriptor     <- CatalogManagementService.validateSubmitOnDescriptor(eService, agreement.descriptorId)
      consumer       <- tenantManagementService
        .getTenantById(agreement.consumerId)
      updated        <- submit(agreement, eService, descriptor, consumer, payload)
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
      _              <- verifyAgreementActivation(agreement)
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      descriptor     <- CatalogManagementService.validateActivationOnDescriptor(eService, agreement.descriptorId)
      consumer       <- tenantManagementService
        .getTenantById(agreement.consumerId)
      updated        <- activate(agreement, eService, descriptor, consumer, requesterOrgId)
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
      eService       <- catalogManagementService
        .getEServiceById(agreement.eserviceId)
      descriptor     <- eService.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(eService.id, agreement.descriptorId))
      consumer       <- tenantManagementService
        .getTenantById(agreement.consumerId)
      updated        <- suspend(agreement, descriptor, consumer, requesterOrgId)
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

    def upgradeAgreement(
      oldAgreementId: UUID,
      newDescriptor: CatalogDescriptor
    ): Future[AgreementManagement.Agreement] = for {
      stamp <- getUidFutureUUID(contexts).map(AgreementManagement.Stamp(_, offsetDateTimeSupplier.get()))
      upgradeSeed = AgreementManagement.UpgradeAgreementSeed(descriptorId = newDescriptor.id, stamp)
      newAgreement <- agreementManagementService.upgradeById(oldAgreementId, upgradeSeed)
      payload = getClientUpgradePayload(newAgreement, newDescriptor)
      _ <- authorizationManagementService.updateAgreementAndEServiceStates(
        newAgreement.eserviceId,
        newAgreement.consumerId,
        payload
      )
    } yield newAgreement

    def createNewDraftAgreement(agreement: PersistentAgreement, newDescriptorId: UUID) =
      for {
        _            <- verifyConflictingAgreements(agreement, Draft :: Nil)
        newAgreement <- agreementManagementService.createAgreement(
          AgreementManagement.AgreementSeed(
            eserviceId = agreement.eserviceId,
            descriptorId = newDescriptorId,
            producerId = agreement.producerId,
            consumerId = agreement.consumerId,
            verifiedAttributes = agreement.verifiedAttributes.map(a => AgreementManagement.AttributeSeed(a.id)),
            certifiedAttributes = agreement.certifiedAttributes.map(a => AgreementManagement.AttributeSeed(a.id)),
            declaredAttributes = agreement.declaredAttributes.map(a => AgreementManagement.AttributeSeed(a.id)),
            consumerNotes = agreement.consumerNotes
          )
        )
        _            <- createAndCopyDocumentsForClonedAgreement(agreement, newAgreement)
      } yield newAgreement

    val result: Future[Agreement] = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      tenant         <- tenantManagementService.getTenantById(requesterOrgId)
      agreement      <- agreementId.toFutureUUID.flatMap(agreementManagementService.getAgreementById)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      _              <- agreement.assertUpgradableState.toFuture
      eService       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      newDescriptor  <- CatalogManagementService.getEServiceNewerPublishedDescriptor(eService, agreement.descriptorId)
      _              <- validateCertifiedAttributes(newDescriptor, tenant).whenA(eService.producerId != requesterOrgId)
      newAgreement   <-
        if (verifiedAndDeclareSatisfied(agreement, newDescriptor, tenant)) upgradeAgreement(agreement.id, newDescriptor)
        else createNewDraftAgreement(agreement, newDescriptor.id)
      _ <- archivingEservicesQueueService.send[ArchiveEvent](ArchiveEvent(agreement.id, offsetDateTimeSupplier.get()))
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
        .unlessA(agreement.state == Rejected)
      eService       <- catalogManagementService
        .getEServiceById(agreement.eserviceId)
      _              <- verifyCloningConflictingAgreements(requesterOrgId, agreement.eserviceId)
      consumer       <- tenantManagementService
        .getTenantById(requesterOrgId)
      descriptor     <- eService.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(eService.id, agreement.descriptorId))
      _              <- validateCertifiedAttributes(descriptor, consumer).whenA(eService.producerId != consumer.id)
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
        state = agreement.state.toManagement,
        certifiedAttributes = agreement.certifiedAttributes.map(_.toManagement),
        declaredAttributes = agreement.declaredAttributes.map(_.toManagement),
        verifiedAttributes = agreement.verifiedAttributes.map(_.toManagement),
        suspendedByConsumer = agreement.suspendedByConsumer,
        suspendedByProducer = agreement.suspendedByProducer,
        suspendedByPlatform = agreement.suspendedByPlatform,
        consumerNotes = agreementUpdatePayload.consumerNotes.some,
        stamps = agreement.stamps.toManagement
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
    newDescriptor: CatalogDescriptor
  ): ClientAgreementAndEServiceDetailsUpdate = {
    ClientAgreementAndEServiceDetailsUpdate(
      agreementId = newAgreement.id,
      agreementState = toClientState(newAgreement.state.toPersistent),
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
      eService       <- catalogManagementService
        .getEServiceById(agreement.eserviceId)
      consumer       <- tenantManagementService
        .getTenantById(agreement.consumerId)
      descriptor     <- eService.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(eService.id, agreement.descriptorId))
      uid            <- getUidFutureUUID(contexts)
      updated        <- reject(
        agreement,
        eService,
        descriptor,
        consumer,
        payload,
        PersistentStamp(uid, offsetDateTimeSupplier.get())
      )
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, SUPPORT_ROLE) {
    val operationLabel =
      s"Retrieving agreements by EServices $eservicesIds, Consumers $consumersIds, Producers $producersIds, states = $states, showOnlyUpgradeable = $showOnlyUpgradeable"
    logger.info(operationLabel)

    val result: Future[Agreements] = for {
      statesEnum <- parseArrayParameters(states).traverse(AgreementState.fromValue).toFuture
      agreements <- ReadModelAgreementQueries.listAgreements(
        parseArrayParameters(eservicesIds),
        parseArrayParameters(consumersIds),
        parseArrayParameters(producersIds),
        parseArrayParameters(descriptorsIds),
        statesEnum,
        showOnlyUpgradeable,
        offset,
        limit
      )
      apiAgreements = agreements.results
    } yield Agreements(results = apiAgreements.map(_.toApi), totalCount = agreements.totalCount)

    onComplete(result) { getAgreementsResponse[Agreements](operationLabel)(getAgreements200) }

  }

  override def getAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, INTERNAL_ROLE, SUPPORT_ROLE) {
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

  override def computeAgreementsByAttribute(
    payload: ComputeAgreementStatePayload
  )(implicit contexts: Seq[(String, String)]): Route = authorize(ADMIN_ROLE, INTERNAL_ROLE, M2M_ROLE) {
    val operationLabel = s"Recalculating agreements status for attribute ${payload.attributeId}"
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

    def updateAgreement(agreement: AgreementManagement.Agreement)(fsmState: PersistentAgreementState): Future[Unit] = {
      val newSuspendedByPlatform = suspendedByPlatformFlag(fsmState)

      val finalState = agreementStateByFlags(
        fsmState,
        agreement.suspendedByProducer,
        agreement.suspendedByConsumer,
        newSuspendedByPlatform
      )

      val seed = AgreementManagement.UpdateAgreementSeed(
        state = finalState.toManagement,
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
          (agreement.state == AgreementManagement.AgreementState.ACTIVE && finalState == Suspended) ||
            (agreement.state == AgreementManagement.AgreementState.SUSPENDED && finalState == Active)
        )

      (updateAgreement >> updateClientState)
        .whenA(
          newSuspendedByPlatform != agreement.suspendedByPlatform && allowedStateTransitions
            .get(agreement.state)
            .exists(_.contains(finalState.toManagement))
        )
    }

    def updateStates(consumerAttributes: List[PersistentTenantAttribute], eServices: Map[UUID, CatalogItem])(
      agreement: PersistentAgreement
    ): Future[Unit] =
      eServices
        .get(agreement.eserviceId)
        .flatMap(_.descriptors.find(_.id == agreement.descriptorId))
        .map(AgreementStateByAttributesFSM.nextState(agreement, _, consumerAttributes))
        .fold {
          logger.error(
            s"Descriptor ${agreement.descriptorId} Service ${agreement.eserviceId} not found for agreement ${agreement.id}"
          )
          Future.unit
        }(updateAgreement(agreement.toManagement))

    val updatableStates = allowedStateTransitions.map { case (startingState, _) => startingState }.toSeq

    def eServiceContainsAttribute(attributeId: UUID)(eService: CatalogItem): Boolean = {
      val certified = eService.descriptors.flatMap(_.attributes.certified)
      val declared  = eService.descriptors.flatMap(_.attributes.declared)
      val verified  = eService.descriptors.flatMap(_.attributes.verified)
      (certified ++ declared ++ verified)
        .flatMap(_.map(_.id))
        .exists(_ == attributeId)
    }

    val result: Future[Unit] = for {
      agreements <- agreementManagementService.getAgreements(
        consumerId = payload.consumer.id.some,
        states = updatableStates.map(_.toPersistent)
      )
      attributes <- payload.consumer.attributes.toList.traverse(PersistentTenantAttribute.fromAPI).toFuture
      uniqueEServiceIds = agreements.map(_.eserviceId).distinct
      // Not using Future.traverse to not overload our backend. Execution time is not critical for this job
      eServices <- uniqueEServiceIds.traverse(catalogManagementService.getEServiceById)
      filteredEServices = eServices.filter(eServiceContainsAttribute(payload.attributeId))
      eServicesMap      = filteredEServices.fproductLeft(_.id).toMap
      filteredAgreement = agreements.filter(a => filteredEServices.exists(_.id == a.eserviceId))
      _ <- filteredAgreement.traverse(updateStates(attributes, eServicesMap))
    } yield ()

    onComplete(result) {
      computeAgreementsByAttributeResponse[Unit](operationLabel)(_ => computeAgreementsByAttribute204)
    }
  }

  private def submit(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant,
    payload: AgreementSubmissionPayload
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, descriptor, consumer.attributes)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              = agreementStateByFlags(nextStateByAttributes, None, None, suspendedByPlatform)

    def calculateStamps(state: PersistentAgreementState, stamp: PersistentStamp): Future[PersistentStamps] =
      state match {
        case Draft                      => Future.successful(agreement.stamps)
        case Pending                    =>
          Future.successful(agreement.stamps.copy(submission = stamp.some))
        case Active                     =>
          Future.successful(agreement.stamps.copy(submission = stamp.some, activation = stamp.some))
        case MissingCertifiedAttributes => Future.successful(agreement.stamps)
        case _                          => Future.failed(AgreementNotInExpectedState(agreement.id.toString, newState))
      }

    def validateResultState(state: PersistentAgreementState) = Future
      .failed(AgreementSubmissionFailed(agreement.id))
      .unlessA(
        List(Pending, Active)
          .contains(state)
      )

    def getUpdateSeed(stamps: PersistentStamps): AgreementManagement.UpdateAgreementSeed =
      if (newState == Active)
        AgreementManagement.UpdateAgreementSeed(
          state = newState.toManagement,
          certifiedAttributes = matchingCertifiedAttributes(descriptor, consumer),
          declaredAttributes = matchingDeclaredAttributes(descriptor, consumer),
          verifiedAttributes = matchingVerifiedAttributes(eService, descriptor, consumer),
          suspendedByConsumer = agreement.suspendedByConsumer,
          suspendedByProducer = agreement.suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform,
          consumerNotes = payload.consumerNotes,
          stamps = stamps.toManagement
        )
      else
        AgreementManagement.UpdateAgreementSeed(
          state = newState.toManagement,
          certifiedAttributes = Nil,
          declaredAttributes = Nil,
          verifiedAttributes = Nil,
          suspendedByConsumer = None,
          suspendedByProducer = None,
          suspendedByPlatform = suspendedByPlatform,
          consumerNotes = payload.consumerNotes,
          stamps = stamps.toManagement
        )

    def validateConsumerEmails(agreement: PersistentAgreement): Future[Unit] =
      for {
        consumer <- tenantManagementService
          .getTenantById(agreement.consumerId)
        _        <-
          if (!consumer.mails.exists(_.kind == PersistentTenantMailKind.ContactEmail))
            Future.failed(ConsumerWithNotValidEmail(agreement.id, agreement.consumerId))
          else Future.unit
      } yield ()

    def createContractAndSendMail(
      agreement: PersistentAgreement,
      seed: AgreementManagement.UpdateAgreementSeed
    ): Future[Unit] =
      for {
        producer <- tenantManagementService
          .getTenantById(agreement.producerId)
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
      uid    <- getUidFutureUUID(contexts)
      _      <- if (agreement.state == Draft) validateConsumerEmails(agreement) else Future.unit
      stamps <- calculateStamps(newState, PersistentStamp(uid, offsetDateTimeSupplier.get()))
      updateSeed = getUpdateSeed(stamps)
      updated <- agreementManagementService.updateAgreement(agreement.id, updateSeed)

      agreements <- agreementManagementService
        .getAgreements(
          consumerId = agreement.consumerId.some,
          eserviceId = agreement.eserviceId.some,
          states = List(Active, Suspended)
        )
        .map(_.filterNot(_.id == agreement.id))

      _ <-
        if (activeOrSuspended(newState.toManagement))
          Future
            .traverse(agreements)(a =>
              agreementManagementService.updateAgreement(
                a.id,
                AgreementManagement.UpdateAgreementSeed(
                  state = AgreementManagement.AgreementState.ARCHIVED,
                  certifiedAttributes = a.certifiedAttributes.map(_.toManagement),
                  declaredAttributes = a.declaredAttributes.map(_.toManagement),
                  verifiedAttributes = a.verifiedAttributes.map(_.toManagement),
                  stamps = a.stamps.toManagement
                    .copy(archiving = AgreementManagement.Stamp(uid, offsetDateTimeSupplier.get()).some)
                )
              )
            )
            .map(_ => ())
        else Future.unit

      // * If the state is not active due to flags this will return an applicative error
      // * That's why this check is performed downstream
      _ <- validateResultState(newState)
      _ <-
        if (activeOrSuspended(newState.toManagement))
          authorizationManagementService.updateStateOnClients(
            eServiceId = agreement.eserviceId,
            consumerId = agreement.consumerId,
            agreementId = agreement.id,
            state = toClientState(newState)
          )
        else Future.unit
      _ <-
        if (updated.state == AgreementManagement.AgreementState.ACTIVE && agreements.isEmpty)
          createContractAndSendMail(updated.toPersistent, updateSeed)
        else Future.unit
    } yield updated
  }

  private def activeOrSuspended(newState: AgreementManagement.AgreementState): Boolean =
    newState == AgreementManagement.AgreementState.ACTIVE || newState == AgreementManagement.AgreementState.SUSPENDED

  private def activate(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant,
    requesterOrgId: UUID
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, descriptor, consumer.attributes)
    val suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, Active)
    val suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, Active)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)
    val newState              =
      agreementStateByFlags(nextStateByAttributes, suspendedByProducer, suspendedByConsumer, suspendedByPlatform)
    val firstActivation       =
      agreement.state == Pending && newState == Active

    def getUpdateSeed(): Future[AgreementManagement.UpdateAgreementSeed] = getUidFutureUUID(contexts).map { uid =>
      if (firstActivation)
        AgreementManagement.UpdateAgreementSeed(
          state = newState.toManagement,
          certifiedAttributes = matchingCertifiedAttributes(descriptor, consumer),
          declaredAttributes = matchingDeclaredAttributes(descriptor, consumer),
          verifiedAttributes = matchingVerifiedAttributes(eService, descriptor, consumer),
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform,
          stamps =
            agreement.stamps.copy(activation = PersistentStamp(uid, offsetDateTimeSupplier.get()).some).toManagement
        )
      else {
        val suspensionByConsumerStamp = suspendedByConsumerStamp(agreement, requesterOrgId, Active, uid)
        val suspensionByProducerStamp = suspendedByProducerStamp(agreement, requesterOrgId, Active, uid)

        AgreementManagement.UpdateAgreementSeed(
          state = newState.toManagement,
          certifiedAttributes = agreement.certifiedAttributes.map(_.toManagement),
          declaredAttributes = agreement.declaredAttributes.map(_.toManagement),
          verifiedAttributes = agreement.verifiedAttributes.map(_.toManagement),
          suspendedByConsumer = suspendedByConsumer,
          suspendedByProducer = suspendedByProducer,
          suspendedByPlatform = suspendedByPlatform,
          stamps = agreement.stamps
            .copy(suspensionByConsumer = suspensionByConsumerStamp, suspensionByProducer = suspensionByProducerStamp)
            .toManagement,
          suspendedAt = if (newState == Active) None else agreement.suspendedAt
        )
      }
    }

    val failureStates = List(Draft, Pending, MissingCertifiedAttributes)

    def failOnActivationFailure() = Future
      .failed(AgreementActivationFailed(agreement.id))
      .whenA(failureStates.contains(newState))

    def performActivation(seed: AgreementManagement.UpdateAgreementSeed, agreement: PersistentAgreement): Future[Unit] =
      for {
        producer <- tenantManagementService
          .getTenantById(agreement.producerId)
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
      seed               <- getUpdateSeed()
      existingAgreements <- agreementManagementService.getAgreements(
        consumerId = Some(agreement.consumerId),
        eserviceId = Some(agreement.eserviceId)
      )
      archivables = extractArchivables(agreement.id, existingAgreements)
      updated <- agreementManagementService.updateAgreement(agreement.id, seed)
      _       <-
        Future.traverse(archivables)(archive).map(_ => ())
      _       <- failOnActivationFailure()
      _       <- authorizationManagementService
        .updateStateOnClients(
          eServiceId = agreement.eserviceId,
          consumerId = agreement.consumerId,
          agreementId = agreement.id,
          state = toClientState(newState)
        )
      _       <- if (firstActivation) performActivation(seed, updated.toPersistent) else Future.unit
    } yield updated
  }

  private def extractArchivables(
    activatingAgreementId: UUID,
    agreements: Seq[PersistentAgreement]
  ): Seq[PersistentAgreement] =
    agreements.filter(a => ARCHIVABLE_STATES.contains(a.state) && a.id != activatingAgreementId)

  private def archive(
    agreement: PersistentAgreement
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = for {
    uid <- getUidFutureUUID(contexts)
    seed: AgreementManagement.UpdateAgreementSeed = AgreementManagement.UpdateAgreementSeed(
      state = AgreementManagement.AgreementState.ARCHIVED,
      certifiedAttributes = agreement.certifiedAttributes.map(_.toManagement),
      declaredAttributes = agreement.declaredAttributes.map(_.toManagement),
      verifiedAttributes = agreement.verifiedAttributes.map(_.toManagement),
      suspendedByConsumer = agreement.suspendedByConsumer,
      suspendedByProducer = agreement.suspendedByProducer,
      suspendedByPlatform = agreement.suspendedByPlatform,
      stamps = agreement.stamps.toManagement
        .copy(archiving = AgreementManagement.Stamp(uid, offsetDateTimeSupplier.get()).some)
    )
    updated <- agreementManagementService.updateAgreement(agreement.id, seed)
  } yield updated

  private def sendActivationEnvelope(
    agreement: PersistentAgreement,
    producerTenant: PersistentTenant,
    consumerTenant: PersistentTenant,
    eservice: CatalogItem
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Unit] = {
    val envelopeId: UUID = UUIDSupplier.get()

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

    val envelope: Future[TextMail] = for {
      producerSelfcareId <- producerTenant.selfcareId.toFuture(SelfcareIdNotFound(producerTenant.id))
      consumerSelfcareId <- consumerTenant.selfcareId.toFuture(SelfcareIdNotFound(consumerTenant.id))
      activationDate     <- agreement.stamps.activation.map(_.when).toFuture(StampNotFound("activation"))
      producer           <- partyProcessService.getInstitution(producerSelfcareId)
      consumer           <- partyProcessService.getInstitution(consumerSelfcareId)
      version            <- eservice.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(eServiceId = eservice.id, descriptorId = agreement.descriptorId))
      producerAddress    <- Mail.addresses(producer.digitalAddress).toFuture
      consumerAddress    <- Mail.addresses(consumer.digitalAddress).toFuture
    } yield TextMail(
      id = envelopeId,
      recipients = producerAddress ++ consumerAddress,
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

    envelope.flatMap(certifiedMailQueueService.send[TextMail]).map(_ => ())
  }

  private def suspend(
    agreement: PersistentAgreement,
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant,
    requesterOrgId: UUID
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val nextStateByAttributes = AgreementStateByAttributesFSM.nextState(agreement, descriptor, consumer.attributes)
    val suspendedByConsumer   = suspendedByConsumerFlag(agreement, requesterOrgId, Suspended)
    val suspendedByProducer   = suspendedByProducerFlag(agreement, requesterOrgId, Suspended)
    val suspendedByPlatform   = suspendedByPlatformFlag(nextStateByAttributes)

    val newState =
      agreementStateByFlags(nextStateByAttributes, suspendedByProducer, suspendedByConsumer, suspendedByPlatform)

    for {
      uid <- getUidFutureUUID(contexts)
      suspensionByConsumerStamp = suspendedByConsumerStamp(agreement, requesterOrgId, Suspended, uid)
      suspensionByProducerStamp = suspendedByProducerStamp(agreement, requesterOrgId, Suspended, uid)
      updateSeed                = AgreementManagement.UpdateAgreementSeed(
        state = newState.toManagement,
        certifiedAttributes = agreement.certifiedAttributes.map(_.toManagement),
        declaredAttributes = agreement.declaredAttributes.map(_.toManagement),
        verifiedAttributes = agreement.verifiedAttributes.map(_.toManagement),
        suspendedByConsumer = suspendedByConsumer,
        suspendedByProducer = suspendedByProducer,
        suspendedByPlatform = suspendedByPlatform,
        stamps = agreement.stamps.toManagement
          .copy(
            suspensionByConsumer = suspensionByConsumerStamp.map(_.toManagement),
            suspensionByProducer = suspensionByProducerStamp.map(_.toManagement)
          ),
        suspendedAt = agreement.suspendedAt.orElse(offsetDateTimeSupplier.get().some)
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

  private def reject(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant,
    payload: AgreementRejectionPayload,
    stamp: PersistentStamp
  )(implicit contexts: Seq[(String, String)]): Future[AgreementManagement.Agreement] = {
    val updateSeed = AgreementManagement.UpdateAgreementSeed(
      state = AgreementManagement.AgreementState.REJECTED,
      certifiedAttributes = matchingCertifiedAttributes(descriptor, consumer),
      declaredAttributes = matchingDeclaredAttributes(descriptor, consumer),
      verifiedAttributes = matchingVerifiedAttributes(eService, descriptor, consumer),
      suspendedByConsumer = None,
      suspendedByProducer = None,
      suspendedByPlatform = None,
      consumerNotes = agreement.consumerNotes,
      rejectionReason = payload.reason.some,
      stamps = agreement.stamps.copy(rejection = stamp.some).toManagement
    )
    agreementManagementService.updateAgreement(agreement.id, updateSeed)
  }

  private def suspendedByConsumerFlag(
    agreement: PersistentAgreement,
    requesterOrgId: UUID,
    destinationState: PersistentAgreementState
  ): Option[Boolean] =
    if (requesterOrgId == agreement.consumerId) Some(destinationState == Suspended)
    else agreement.suspendedByConsumer

  private def suspendedByProducerFlag(
    agreement: PersistentAgreement,
    requesterOrgId: UUID,
    destinationState: PersistentAgreementState
  ): Option[Boolean] =
    if (requesterOrgId == agreement.producerId) Some(destinationState == Suspended)
    else agreement.suspendedByProducer

  private def suspendedByPlatformFlag(fsmState: PersistentAgreementState): Option[Boolean] =
    // TODO Which states enable the suspendedByPlatform?
    List(Suspended, MissingCertifiedAttributes)
      .contains(fsmState)
      .some

  private def suspendedByConsumerStamp(
    agreement: PersistentAgreement,
    requesterOrgId: UUID,
    destinationState: PersistentAgreementState,
    userId: UUID
  ): Option[PersistentStamp] = (requesterOrgId, destinationState) match {
    case (agreement.consumerId, Suspended) =>
      PersistentStamp(who = userId, when = offsetDateTimeSupplier.get()).some
    case (agreement.consumerId, _)         => None
    case _                                 => agreement.stamps.suspensionByConsumer
  }

  private def suspendedByProducerStamp(
    agreement: PersistentAgreement,
    requesterOrgId: UUID,
    destinationState: PersistentAgreementState,
    userId: UUID
  ): Option[PersistentStamp] = (requesterOrgId, destinationState) match {
    case (agreement.producerId, Suspended) =>
      PersistentStamp(who = userId, when = offsetDateTimeSupplier.get()).some
    case (agreement.producerId, _)         => None
    case _                                 => agreement.stamps.suspensionByProducer
  }

  private def agreementStateByFlags(
    stateByAttribute: PersistentAgreementState,
    suspendedByProducer: Option[Boolean],
    suspendedByConsumer: Option[Boolean],
    suspendedByPlatform: Option[Boolean]
  ): PersistentAgreementState =
    (stateByAttribute, suspendedByProducer, suspendedByConsumer, suspendedByPlatform) match {
      case (Active, Some(true), _, _) => Suspended
      case (Active, _, Some(true), _) => Suspended
      case (Active, _, _, Some(true)) => Suspended
      case _                          => stateByAttribute
    }

  private def matchingAttributes(
    eServiceAttributes: Seq[Seq[CatalogAttribute]],
    consumerAttributes: Seq[UUID]
  ): Seq[UUID] =
    eServiceAttributes
      .flatMap(_.map(_.id))
      .intersect(consumerAttributes)

  private def matchingCertifiedAttributes(
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant
  ): Seq[AgreementManagement.CertifiedAttribute] = {
    val attributes: Seq[UUID] = consumer.attributes
      .collect { case a: PersistentCertifiedAttribute => a }
      .filter(_.revocationTimestamp.isEmpty)
      .map(_.id)

    matchingAttributes(descriptor.attributes.certified, attributes).map(AgreementManagement.CertifiedAttribute)
  }

  private def matchingDeclaredAttributes(
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant
  ): Seq[AgreementManagement.DeclaredAttribute] = {
    val attributes: Seq[UUID] = consumer.attributes
      .collect { case a: PersistentDeclaredAttribute => a }
      .filter(_.revocationTimestamp.isEmpty)
      .map(_.id)

    matchingAttributes(descriptor.attributes.declared, attributes).map(AgreementManagement.DeclaredAttribute)
  }

  private def matchingVerifiedAttributes(
    eService: CatalogItem,
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant
  ): Seq[AgreementManagement.VerifiedAttribute] = {
    val attributes: Seq[UUID] =
      consumer.attributes
        .collect { case a: PersistentVerifiedAttribute => a }
        .filter(_.verifiedBy.exists(_.id == eService.producerId))
        .map(_.id)

    matchingAttributes(descriptor.attributes.verified, attributes).map(AgreementManagement.VerifiedAttribute)
  }

  private def verifyRequester(requesterId: UUID, expected: UUID): Future[Unit] =
    Future.failed(OperationNotAllowed(requesterId)).whenA(requesterId != expected)

  private def assertRequesterIsConsumer(requesterOrgId: UUID, agreement: PersistentAgreement): Future[Unit] =
    verifyRequester(requesterOrgId, agreement.consumerId)

  private def assertRequesterIsProducer(requesterOrgId: UUID, agreement: PersistentAgreement): Future[Unit] =
    verifyRequester(requesterOrgId, agreement.producerId)

  private def assertRequesterIsConsumerOrProducer(requesterOrgId: UUID, agreement: PersistentAgreement): Future[Unit] =
    assertRequesterIsConsumer(requesterOrgId, agreement) orElse assertRequesterIsProducer(requesterOrgId, agreement)

  private def verifyCreationConflictingAgreements(consumerId: UUID, payload: AgreementPayload): Future[Unit] = {
    val conflictingStates: List[PersistentAgreementState] =
      List(Draft, Pending, MissingCertifiedAttributes, Active, Suspended)
    verifyConflictingAgreements(consumerId, payload.eserviceId, conflictingStates)
  }

  private def verifyCloningConflictingAgreements(consumerId: UUID, eserviceId: UUID): Future[Unit] = {
    val conflictingStates: List[PersistentAgreementState] =
      List(Draft, Pending, MissingCertifiedAttributes, Active, Suspended)
    verifyConflictingAgreements(consumerId, eserviceId, conflictingStates)
  }

  private def verifySubmissionConflictingAgreements(agreement: PersistentAgreement): Future[Unit] = {
    val conflictingStates: List[PersistentAgreementState] = List(Pending, MissingCertifiedAttributes)
    verifyConflictingAgreements(agreement, conflictingStates)
  }

  private def verifyAgreementActivation(agreement: PersistentAgreement): Future[Unit] = {
    val admittedStates: List[PersistentAgreementState] =
      List(Pending, Suspended)
    if (admittedStates.contains(agreement.state)) Future.unit
    else Future.failed(AgreementNotInExpectedState(agreement.id.toString, agreement.state))
  }

  private def createAndCopyDocumentsForClonedAgreement(
    oldAgreement: PersistentAgreement,
    newAgreement: AgreementManagement.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Seq[AgreementManagement.Document]] = {
    Future.traverse(oldAgreement.consumerDocuments) { doc =>
      val newDocumentId: UUID     = uuidSupplier.get()
      val newDocumentPath: String =
        s"${ApplicationConfiguration.consumerDocumentsPath}/${newAgreement.id.toString}"

      fileManager
        .copy(ApplicationConfiguration.storageContainer, newDocumentPath)(doc.path, newDocumentId.toString, doc.name)
        .flatMap(storageFilePath =>
          agreementManagementService
            .addConsumerDocument(newAgreement.id, doc.toSeed(newDocumentId, storageFilePath))
        )
    }
  }

  private def validateCertifiedAttributes(descriptor: CatalogDescriptor, consumer: PersistentTenant): Future[Unit] =
    Future
      .failed(MissingCertifiedAttributesError(descriptor.id, consumer.id))
      .unlessA(certifiedAttributesSatisfied(descriptor, consumer.attributes))

  private def verifiedAndDeclareSatisfied(
    agreement: PersistentAgreement,
    newDescriptor: CatalogDescriptor,
    tenant: PersistentTenant
  ): Boolean =
    verifiedAttributesSatisfied(agreement, newDescriptor, tenant.attributes) && declaredAttributesSatisfied(
      newDescriptor,
      tenant.attributes
    )

  private def verifyConsumerDoesNotActivatePending(
    agreement: PersistentAgreement,
    requesterOrgUuid: UUID
  ): Future[Unit] =
    Future
      .failed(OperationNotAllowed(requesterOrgUuid))
      .whenA(
        agreement.state == Pending &&
          agreement.consumerId == requesterOrgUuid &&
          agreement.producerId != agreement.consumerId
      )

  private def toClientState(state: PersistentAgreementState): AuthorizationManagement.ClientComponentState =
    state match {
      case Active => AuthorizationManagement.ClientComponentState.ACTIVE
      case _      => AuthorizationManagement.ClientComponentState.INACTIVE
    }

  private def toClientState(state: CatalogDescriptorState): AuthorizationManagement.ClientComponentState =
    state match {
      case Published | Deprecated =>
        AuthorizationManagement.ClientComponentState.ACTIVE
      case _                      => AuthorizationManagement.ClientComponentState.INACTIVE
    }

  private def verifyConflictingAgreements(
    agreement: PersistentAgreement,
    conflictingStates: List[PersistentAgreementState]
  ): Future[Unit] =
    verifyConflictingAgreements(agreement.consumerId, agreement.eserviceId, conflictingStates)

  private def verifyConflictingAgreements(
    consumerId: UUID,
    eServiceId: UUID,
    conflictingStates: List[PersistentAgreementState]
  ): Future[Unit] = {
    for {
      activeAgreement <- agreementManagementService.getAgreements(
        consumerId = Some(consumerId),
        eserviceId = Some(eServiceId),
        states = conflictingStates
      )
      _               <- Future
        .failed(AgreementAlreadyExists(consumerId, eServiceId))
        .whenA(activeAgreement.nonEmpty)
    } yield ()
  }

  private def assertCanWorkOnConsumerDocuments(agreementState: PersistentAgreementState): Future[Unit] =
    Future
      .failed(DocumentsChangeNotAllowed(agreementState))
      .unlessA(Set[PersistentAgreementState](Draft, Pending).contains(agreementState))

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
  ): Route = authorize(ADMIN_ROLE, SUPPORT_ROLE) {
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
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, SUPPORT_ROLE) {
    val operationLabel =
      s"Retrieving producers from agreements with producer name $producerName"
    logger.info(operationLabel)

    val result: Future[CompactOrganizations] = for {
      compactTenants <- ReadModelAgreementQueries.listProducers(producerName, offset, limit)
    } yield CompactOrganizations(results = compactTenants.results, totalCount = compactTenants.totalCount)

    onComplete(result) { getAgreementProducersResponse[CompactOrganizations](operationLabel)(getAgreementProducers200) }
  }

  override def getAgreementConsumers(consumerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, SUPPORT_ROLE) {
    val operationLabel =
      s"Retrieving consumers from agreements with consumer name $consumerName"
    logger.info(operationLabel)

    val result: Future[CompactOrganizations] = for {
      compactTenants <- ReadModelAgreementQueries.listConsumers(consumerName, offset, limit)
    } yield CompactOrganizations(results = compactTenants.results, totalCount = compactTenants.totalCount)

    onComplete(result) { getAgreementConsumersResponse[CompactOrganizations](operationLabel)(getAgreementConsumers200) }
  }

  override def getAgreementEServices(
    eServiceName: Option[String],
    consumersIds: String,
    producersIds: String,
    offset: Int,
    limit: Int
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerCompactEServices: ToEntityMarshaller[CompactEServices],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, SUPPORT_ROLE) {
    val operationLabel =
      s"Retrieving EServices with consumers $consumersIds, producers $producersIds"
    logger.info(operationLabel)

    val result: Future[CompactEServices] = for {
      eservices <- ReadModelAgreementQueries.listEServicesAgreements(
        eServiceName = eServiceName,
        consumersIds = parseArrayParameters(consumersIds),
        producersIds = parseArrayParameters(producersIds),
        offset = offset,
        limit = limit
      )
      apiEServices = eservices.results
    } yield CompactEServices(results = apiEServices, totalCount = eservices.totalCount)

    onComplete(result) {
      getAgreementEServicesResponse[CompactEServices](operationLabel)(getAgreementEServices200)
    }
  }

  override def archiveAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    val operationLabel = s"Archiving agreement $agreementId"
    logger.info(operationLabel)

    def archive(agreement: AgreementManagement.Agreement): Future[AgreementManagement.Agreement] = for {
      uid       <- getUidFutureUUID(contexts)
      agreement <- agreementManagementService.updateAgreement(
        agreement.id,
        AgreementManagement.UpdateAgreementSeed(
          state = AgreementManagement.AgreementState.ARCHIVED,
          certifiedAttributes = agreement.certifiedAttributes,
          declaredAttributes = agreement.declaredAttributes,
          verifiedAttributes = agreement.verifiedAttributes,
          suspendedByConsumer = agreement.suspendedByConsumer,
          suspendedByProducer = agreement.suspendedByProducer,
          suspendedByPlatform = agreement.suspendedByPlatform,
          stamps = agreement.stamps.copy(archiving = AgreementManagement.Stamp(uid, offsetDateTimeSupplier.get()).some)
        )
      )
      _         <- authorizationManagementService.updateStateOnClients(
        eServiceId = agreement.eserviceId,
        consumerId = agreement.consumerId,
        agreementId = agreement.id,
        state = AuthorizationManagement.ClientComponentState.INACTIVE
      )
    } yield agreement

    val result: Future[Agreement] = for {
      requesterOrgId <- getOrganizationIdFutureUUID(contexts)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      _              <- assertRequesterIsConsumer(requesterOrgId, agreement)
      updated        <- archive(agreement.toManagement)
      _ <- archivingPurposesQueueService.send[ArchiveEvent](ArchiveEvent(updated.id, offsetDateTimeSupplier.get()))
      _ <- archivingEservicesQueueService.send[ArchiveEvent](ArchiveEvent(updated.id, offsetDateTimeSupplier.get()))
    } yield updated.toApi

    onComplete(result) {
      archiveAgreementResponse[Agreement](operationLabel)(archiveAgreement200)
    }
  }
}
