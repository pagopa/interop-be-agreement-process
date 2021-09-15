package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{VerifiedAttribute, VerifiedAttributeSeed}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.error.{AgreementNotFound, DescriptorNotFound}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
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
    "org.wartremover.warts.Nothing",
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

  /** Code: 200, Message: audiences found, DataType: Audience
    * Code: 400, Message: Invalid ID supplied, DataType: Problem
    */
  override def getAudienceByAgreementId(agreementId: String)(implicit
    toEntityMarshallerAudience: ToEntityMarshaller[Audience],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info(s"Getting audience for agreement $agreementId")
    val result = for {
      bearerToken     <- extractBearer(contexts)
      agreement       <- agreementManagementService.getAgreementById(bearerToken, agreementId)
      activeAgreement <- AgreementManagementService.isActive(agreement)
      eservice        <- catalogManagementService.getEServiceById(bearerToken, activeAgreement.eserviceId)
      activeEservice  <- CatalogManagementService.validateOperationOnDescriptor(eservice, agreement.descriptorId)
    } yield Audience(activeEservice.name, activeEservice.audience)

    onComplete(result) {
      case Success(res) => getAudienceByAgreementId200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"error while retrieving audience for agreement $agreementId")
        getAudienceByAgreementId400(errorResponse)
    }
  }

  override def activateAgreement(
    agreementId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info(s"Activating agreement $agreementId")
    val result = for {
      bearerToken      <- extractBearer(contexts)
      agreement        <- agreementManagementService.getAgreementById(bearerToken, agreementId)
      pendingAgreement <- AgreementManagementService.isPending(agreement)
      consumerAttributesIds <- partyManagementService.getPartyAttributes(
        bearerToken,
        pendingAgreement.consumerId.toString
      )
      eservice       <- catalogManagementService.getEServiceById(bearerToken, pendingAgreement.eserviceId)
      activeEservice <- CatalogManagementService.validateActivationOnDescriptor(eservice, agreement.descriptorId)
      _ <- AgreementManagementService.verifyAttributes(
        consumerAttributesIds,
        activeEservice.attributes,
        agreement.verifiedAttributes
      )
      _ <- agreementManagementService.activateById(bearerToken, agreementId)
    } yield ()

    onComplete(result) {
      case Success(_) => activateAgreement204
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while activating agreement $agreementId")
        activateAgreement400(errorResponse)
    }
  }

  /** Code: 201, Message: Agreement created., DataType: Agreement
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def createAgreement(agreementPayload: AgreementPayload)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {

    logger.info(s"Creating agreement $agreementPayload")
    val result = for {
      bearerToken <- extractBearer(contexts)
      consumerAgreements <- agreementManagementService.getAgreements(
        bearerToken = bearerToken,
        consumerId = Some(agreementPayload.consumerId.toString)
      )
      validPayload               <- AgreementManagementService.validatePayload(agreementPayload, consumerAgreements)
      eservice                   <- catalogManagementService.getEServiceById(bearerToken, validPayload.eserviceId)
      activeEservice             <- CatalogManagementService.validateOperationOnDescriptor(eservice, agreementPayload.descriptorId)
      _                          <- CatalogManagementService.verifyProducerMatch(activeEservice.producerId, validPayload.producerId)
      consumerVerifiedAttributes <- AgreementManagementService.extractVerifiedAttribute(consumerAgreements)
      verifiedAttributes         <- CatalogManagementService.flattenAttributes(activeEservice.attributes.verified)
      verifiedAttributeSeeds <- AgreementManagementService.applyImplicitVerification(
        verifiedAttributes,
        consumerVerifiedAttributes
      )
      agreement    <- agreementManagementService.createAgreement(bearerToken, validPayload, verifiedAttributeSeeds)
      apiAgreement <- getApiAgreement(bearerToken, agreement)
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
    status: Option[String]
  )(implicit
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val result = for {
      bearerToken   <- extractBearer(contexts)
      agreements    <- agreementManagementService.getAgreements(bearerToken, producerId, consumerId, eserviceId, status)
      apiAgreements <- Future.traverse(agreements)(agreement => getApiAgreement(bearerToken, agreement))
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
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement],
    contexts: Seq[(String, String)]
  ): Route = {
    val result: Future[Agreement] = for {
      bearerToken  <- extractBearer(contexts)
      agreement    <- agreementManagementService.getAgreementById(bearerToken, agreementId)
      apiAgreement <- getApiAgreement(bearerToken, agreement)
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

  private def getApiAgreement(bearerToken: String, agreement: ManagementAgreement): Future[Agreement] = {
    for {
      eservice <- catalogManagementService.getEServiceById(bearerToken, agreement.eserviceId)
      descriptor <- eservice.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(DescriptorNotFound(agreement.eserviceId.toString, agreement.descriptorId.toString))
      producer  <- partyManagementService.getOrganization(bearerToken, agreement.producerId)
      consumer  <- partyManagementService.getOrganization(bearerToken, agreement.consumerId)
      attribute <- Future.traverse(agreement.verifiedAttributes)(getApiAttribute)
    } yield Agreement(
      id = agreement.id,
      producer = Organization(id = producer.institutionId, name = producer.description),
      consumer = Organization(id = consumer.institutionId, name = producer.description),
      eservice = EService(id = eservice.id, name = eservice.name, version = descriptor.version),
      attributes = attribute
    )
  }

  private def getApiAttribute(verifiedAttribute: VerifiedAttribute): Future[Attribute] = {
    for {
      att     <- attributeManagementService.getAttribute(verifiedAttribute.id.toString)
      idUuuid <- Future.fromTry(Try(UUID.fromString(att.id)))
    } yield Attribute(
      id = idUuuid,
      code = att.code,
      description = att.description,
      origin = att.origin,
      name = att.name,
      verified = Some(verifiedAttribute.verified),
      verificationDate = verifiedAttribute.verificationDate,
      validityTimespan = verifiedAttribute.validityTimespan
    )

  }

  /** Code: 204, Message: No Content
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def verifyAgreementAttribute(agreementId: String, attributeId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info(s"Marking agreement $agreementId verified attribute $attributeId as verified.")

    val result = for {
      bearerToken <- extractBearer(contexts)
      attributeUUID <- Future.fromTry(Try {
        UUID.fromString(attributeId)
      })
      _ <- agreementManagementService.markVerifiedAttribute(
        bearerToken,
        agreementId,
        VerifiedAttributeSeed(attributeUUID, verified = true)
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
}
