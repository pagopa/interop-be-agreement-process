package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Agreement, AgreementPayload, Audience, Problem}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementService,
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
  partyManagementService: PartyManagementService
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
      activeAgreement <- agreementManagementService.checkAgreementActivation(agreement)
      eservice        <- catalogManagementService.getEServiceById(bearerToken, activeAgreement.eserviceId)
      activeEservice  <- catalogManagementService.checkEServiceActivation(eservice)
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
      pendingAgreement <- agreementManagementService.isPending(agreement)
      consumerAttributesIds <- partyManagementService.getPartyAttributes(
        bearerToken,
        pendingAgreement.consumerId.toString
      )
      eservice       <- catalogManagementService.getEServiceById(bearerToken, pendingAgreement.eserviceId)
      activeEservice <- catalogManagementService.checkEServiceActivation(eservice)
      _ <- agreementManagementService.verifyAttributes(
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
    // TODO the request to get a set of agreements, is performed twice. Verify if is possible to reduce to one,
    //  maintaining the right semantic
    val result = for {
      bearerToken <- extractBearer(contexts)
      // TODO inside of validatePayload is performed a get agreements request
      validatedPayload <- agreementManagementService.validatePayload(bearerToken, agreementPayload)
      eservice         <- catalogManagementService.getEServiceById(bearerToken, validatedPayload.eserviceId)
      activeEservice   <- catalogManagementService.checkEServiceActivation(eservice)
      _                <- catalogManagementService.verifyProducerMatch(activeEservice.producerId, validatedPayload.producerId)
      // TODO here another get agreements request
      consumerAgreements <- agreementManagementService.getAgreements(
        bearerToken = bearerToken,
        consumerId = Some(validatedPayload.consumerId.toString)
      )
      consumerVerifiedAttributes <- agreementManagementService.extractVerifiedAttribute(consumerAgreements)
      verifiedAttributes         <- catalogManagementService.flattenAttributes(activeEservice.attributes.verified)
      verifiedAttributeSeeds <- agreementManagementService.applyImplicitVerification(
        verifiedAttributes,
        consumerVerifiedAttributes
      )
      agreement <- agreementManagementService.createAgreement(bearerToken, agreementPayload, verifiedAttributeSeeds)
    } yield Agreement(agreement.id)

    onComplete(result) {
      case Success(agreement) => createAgreement201(agreement)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"Error while creating agreement $agreementPayload")
        createAgreement400(errorResponse)
    }
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
      _ <- agreementManagementService.markAttributeAsVerified(bearerToken, agreementId, attributeUUID)
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
