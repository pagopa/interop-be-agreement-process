package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.model.{
  AgreementDocumentSeed,
  AgreementSeed,
  VerifiedAttribute,
  VerifiedAttributeSeed
}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.agreementprocess.api.AgreementApiService
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service.AgreementManagementService.agreementStateToApi
import it.pagopa.interop.agreementprocess.service.CatalogManagementService.descriptorStateToApi
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.model.{
  AttributeValue => CatalogAttributeValue,
  EService => CatalogEService
}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, M2M_ROLE}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.{EitherOps, OptionOps, StringOps}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError
import it.pagopa.interop.commons.utils.service.UUIDSupplier

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success}

final case class AgreementApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeManagementService: AttributeManagementService,
  authorizationManagementService: AuthorizationManagementService,
  jwtReader: JWTReader,
  fileManager: FileManager,
  pdfCreator: PDFCreator,
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends AgreementApiService {

  private[this] val agreementTemplate = Source
    .fromResource("agreementTemplate/index.html")
    .getLines()
    .mkString(System.lineSeparator())

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def activateAgreement(agreementId: String, partyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Activating agreement {}", agreementId)
    val result = for {
      agreement <- agreementManagementService.getAgreementById(agreementId)
      _         <- verifyAgreementActivationEligibility(agreement)
      eservice  <- catalogManagementService.getEServiceById(agreement.eserviceId)
      _         <- verifyAttributes(agreement, eservice)
      activated <- activate(agreement, eservice, partyId)
      _         <- authorizationManagementService.updateStateOnClients(
        eServiceId = activated.eserviceId,
        consumerId = activated.consumerId,
        agreementId = activated.id,
        state = AuthorizationManagementDependency.ClientComponentState.ACTIVE
      )
    } yield ()

    onComplete(result) {
      case Success(_)  => activateAgreement204
      case Failure(ex) =>
        logger.error(s"Error while activating agreement $agreementId", ex)
        activateAgreement400(problemOf(StatusCodes.BadRequest, ActivateAgreementError(agreementId)))
    }
  }

  private def verifyAttributes(
    agreement: AgreementManagementDependency.Agreement,
    eservice: CatalogManagementDependency.EService
  )(implicit contexts: Seq[(String, String)]): Future[Boolean] = for {
    consumerAttributes    <- partyManagementService.getPartyAttributes(agreement.consumerId)
    consumerAttributesIds <- Future.traverse(consumerAttributes)(a =>
      attributeManagementService.getAttributeByOriginAndCode(a.origin, a.code).map(_.id)
    )
    activeEservice        <- CatalogManagementService.validateActivationOnDescriptor(eservice, agreement.descriptorId)
    result                <- AgreementManagementService.verifyAttributes(
      consumerAttributesIds,
      activeEservice.attributes,
      agreement.verifiedAttributes
    )
  } yield result

  private def activate(
    agreement: AgreementManagementDependency.Agreement,
    eservice: CatalogManagementDependency.EService,
    partyId: String
  )(implicit contexts: Seq[(String, String)]) = for {
    producer <- partyManagementService.getInstitution(agreement.producerId)
    consumer <- partyManagementService.getInstitution(agreement.consumerId)
    document <- pdfCreator.create(agreementTemplate, eservice.name, producer.description, consumer.description)
    fileInfo = FileInfo("agreementDocument", document.getName, MediaTypes.`application/pdf`)
    path <- fileManager.store(ApplicationConfiguration.storageContainer, ApplicationConfiguration.storagePath)(
      uuidSupplier.get,
      (fileInfo, document)
    )
    _    <- agreementManagementService.addAgreementDocument(
      agreement.id.toString,
      AgreementDocumentSeed(fileInfo.contentType.value, path)
    )
    changeStateDetails <- AgreementManagementService.getStateChangeDetails(agreement, partyId)
    result             <- agreementManagementService.activateById(agreement.id.toString, changeStateDetails)
  } yield result

  /** Code: 204, Message: Active agreement suspended.
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def suspendAgreement(agreementId: String, partyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Suspending agreement {}", agreementId)
    val result = for {
      agreement          <- agreementManagementService.getAgreementById(agreementId)
      changeStateDetails <- AgreementManagementService.getStateChangeDetails(agreement, partyId)
      _                  <- agreementManagementService.suspendById(agreementId, changeStateDetails)
      _                  <- authorizationManagementService.updateStateOnClients(
        eServiceId = agreement.eserviceId,
        consumerId = agreement.consumerId,
        agreementId = agreement.id,
        state = AuthorizationManagementDependency.ClientComponentState.INACTIVE
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

  /** Code: 201, Message: Agreement created., DataType: Agreement
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def createAgreement(agreementPayload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Creating agreement {}", agreementPayload)
    val result = for {
      consumerAgreements <- agreementManagementService.getAgreements(consumerId =
        Some(agreementPayload.consumerId.toString)
      )
      validPayload       <- AgreementManagementService.validatePayload(agreementPayload, consumerAgreements)
      eservice           <- catalogManagementService.getEServiceById(validPayload.eserviceId)
      activeEservice <- CatalogManagementService.validateOperationOnDescriptor(eservice, agreementPayload.descriptorId)
      consumer       <- partyManagementService.getInstitution(agreementPayload.consumerId)
      consumerAttributeIdentifiers <- Future.traverse(consumer.attributes)(a =>
        attributeManagementService.getAttributeByOriginAndCode(a.origin, a.code).map(_.id)
      )
      activatableEservice          <- AgreementManagementService.verifyCertifiedAttributes(
        consumerAttributeIdentifiers,
        activeEservice
      )
      consumerVerifiedAttributes   <- AgreementManagementService.extractVerifiedAttribute(consumerAgreements)
      verifiedAttributes     <- CatalogManagementService.flattenAttributes(activatableEservice.attributes.verified)
      verifiedAttributeSeeds <- AgreementManagementService.applyImplicitVerification(
        verifiedAttributes,
        consumerVerifiedAttributes
      )
      agreement              <- agreementManagementService.createAgreement(
        activatableEservice.producerId,
        validPayload,
        verifiedAttributeSeeds
      )
      apiAgreement           <- getApiAgreement(agreement)
    } yield apiAgreement

    onComplete(result) {
      case Success(agreement) => createAgreement201(agreement)
      case Failure(ex)        =>
        logger.error(s"Error while creating agreement $agreementPayload", ex)
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
  ): Route = authorize(ADMIN_ROLE, M2M_ROLE) {
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
      stateEnum                <- state.traverse(AgreementManagementDependency.AgreementState.fromValue).toFuture
      agreements               <- agreementManagementService.getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = stateEnum
      )
      apiAgreementsWithVersion <- Future.traverse(agreements)(getApiAgreement)
      apiAgreements            <- AgreementFilter.filterAgreementsByLatestVersion(latest, apiAgreementsWithVersion)
    } yield apiAgreements

    onComplete(result) {
      case Success(agreement) => getAgreements200(agreement)
      case Failure(ex)        =>
        logger.error(
          s"Error while getting agreements by producer = $producerId, consumer = $consumerId, eservice = $eserviceId, descriptor = $descriptorId, state = $state, latest = $latest",
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
  ): Route = authorize(ADMIN_ROLE, M2M_ROLE) {
    logger.info("Getting agreement by id {}", agreementId)
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

  private def getApiAgreementAttributes(verifiedAttributes: Seq[VerifiedAttribute], eService: CatalogEService)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[AgreementAttributes]] =
    for {
      attributes <- Future.traverse(verifiedAttributes)(getApiAttribute(eService.attributes))
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
      .find(_.id.toString == eServiceAttributeValue.id)
      .toFuture(AgreementAttributeNotFound(eServiceAttributeValue.id))

  private def getApiAttribute(
    attributes: ManagementAttributes
  )(verifiedAttribute: VerifiedAttribute)(implicit contexts: Seq[(String, String)]): Future[Attribute] = {
    val fromSingle: Seq[CatalogAttributeValue] =
      attributes.verified.flatMap(attribute => attribute.single.toList.find(_.id == verifiedAttribute.id.toString))

    val fromGroup: Seq[CatalogAttributeValue] =
      attributes.verified.flatMap(attribute => attribute.group.flatMap(_.find(_.id == verifiedAttribute.id.toString)))

    val allVerifiedAttributes: Map[String, Boolean] =
      (fromSingle ++ fromGroup).map(attribute => attribute.id -> attribute.explicitAttributeVerification).toMap

    for {
      att  <- attributeManagementService.getAttribute(verifiedAttribute.id.toString)
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
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Verifying agreement {} attribute {}", agreementId, attributeId)
    val result = for {
      attributeUUID <- attributeId.toFutureUUID
      _             <- agreementManagementService.markVerifiedAttribute(
        agreementId,
        VerifiedAttributeSeed(attributeUUID, verified = Some(true))
      )
    } yield ()

    onComplete(result) {
      case Success(_)  => verifyAgreementAttribute204
      case Failure(ex) =>
        logger.error(s"Error while verifying agreement $agreementId attribute $attributeId", ex)
        val errorResponse: Problem =
          problemOf(StatusCodes.BadRequest, VerifyAgreementAttributeError(agreementId, attributeId))
        verifyAgreementAttribute404(errorResponse)
    }
  }

  /** Verify if an agreement can be activated.
    * Checks performed:
    * - no other active agreement exists for the same combination of Producer, Consumer, EService, Descriptor
    * - the given agreement is in state Pending (first activation) or Suspended (re-activation)
    * @param agreement to be activated
    * @return
    */
  private def verifyAgreementActivationEligibility(
    agreement: ManagementAgreement
  )(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    for {
      activeAgreement <- agreementManagementService.getAgreements(
        producerId = Some(agreement.producerId.toString),
        consumerId = Some(agreement.consumerId.toString),
        eserviceId = Some(agreement.eserviceId.toString),
        descriptorId = Some(agreement.descriptorId.toString),
        state = Some(AgreementManagementDependency.AgreementState.ACTIVE)
      )
      _               <- Either.cond(activeAgreement.isEmpty, (), ActiveAgreementAlreadyExists(agreement)).toFuture
      _               <- AgreementManagementService.isPending(agreement).recoverWith { case _ =>
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
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Updating agreement {}", agreementId)
    val result = for {
      agreement                      <- agreementManagementService.getAgreementById(agreementId)
      eservice                       <- catalogManagementService.getEServiceById(agreement.eserviceId)
      latestActiveEserviceDescriptor <- eservice.descriptors
        .find(d => d.state == CatalogManagementDependency.EServiceDescriptorState.PUBLISHED)
        .toFuture(DescriptorNotFound(agreement.eserviceId.toString, agreement.descriptorId.toString))
      latestDescriptorVersion = latestActiveEserviceDescriptor.version.toLongOption
      currentVersion = eservice.descriptors.find(d => d.id == agreement.descriptorId).flatMap(_.version.toLongOption)
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
      newAgreement <- agreementManagementService.upgradeById(agreement.id, agreementSeed)
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

  /**
    * Code: 200, Message: Agreement document retrieved, DataType: File
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getAgreementDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    logger.info("Getting agreement by id {}", agreementId)
    val result: Future[HttpEntity.Strict] = for {
      agreement    <- agreementManagementService.getAgreementById(agreementId)
      documentUuid <- documentId.toFutureUUID
      document     <- agreement.document.find(_.id == documentUuid).toFuture(DocumentNotFound(agreementId, documentId))
      byteStream   <- fileManager.get(ApplicationConfiguration.storagePath)(document.path)
    } yield HttpEntity(ContentType(MediaTypes.`application/pdf`), byteStream.toByteArray())

    onComplete(result) {
      case Success(file) => complete(file)
      case Failure(ex)   =>
        logger.error(s"Error while getting document agreement agreementId=$agreementId/documentId=$documentId", ex)
        ex match {
          case ex: DocumentNotFound =>
            val errorResponse: Problem =
              problemOf(StatusCodes.NotFound, ex)
            getAgreementDocument404(errorResponse)
          case ex                   =>
            logger.error(s"Error while getting document agreement agreementId=$agreementId/documentId=$documentId", ex)
            val errorResponse: Problem =
              problemOf(StatusCodes.InternalServerError, GenericError(ex.getMessage))
            complete(StatusCodes.InternalServerError, errorResponse)
        }
    }
  }
}
