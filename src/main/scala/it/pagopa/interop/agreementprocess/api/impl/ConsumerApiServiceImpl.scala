package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.agreementprocess.api.ConsumerApiService
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.RetrieveAttributesError
import it.pagopa.interop.agreementprocess.model.{Attributes, Problem}
import it.pagopa.interop.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.StringOps

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class ConsumerApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeManagementService: AttributeManagementService,
  jwtReader: JWTReader
)(implicit ec: ExecutionContext)
    extends ConsumerApiService {

  val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  /** Code: 200, Message: attributes found, DataType: Attributes
    * Code: 404, Message: Consumer not found, DataType: Problem
    * Code: 400, Message: Invalid ID supplied, DataType: Problem
    */
  override def getAttributesByConsumerId(consumerId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributes: ToEntityMarshaller[Attributes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Getting consumer {} attributes", consumerId)
    val result: Future[Attributes] = for {
      bearerToken <- validateBearer(contexts, jwtReader)
      agreements  <- agreementManagementService.getAgreements(
        consumerId = Some(consumerId),
        state = Some(AgreementManagementDependency.AgreementState.ACTIVE)
      )
      eserviceIds = agreements.map(_.eserviceId)
      eservices           <- Future.traverse(eserviceIds)(catalogManagementService.getEServiceById)
      consumerUuid        <- consumerId.toFutureUUID
      partyAttributes     <- partyManagementService.getPartyAttributes(bearerToken)(consumerUuid)
      eserviceAttributes  <- eservices
        .flatTraverse(eservice => CatalogManagementService.flattenAttributes(eservice.attributes.declared))
      agreementAttributes <- AgreementManagementService.extractVerifiedAttribute(agreements)
      certified           <- Future.traverse(partyAttributes)(a =>
        attributeManagementService.getAttributeByOriginAndCode(a.origin, a.code)
      )
      declared            <- Future.traverse(eserviceAttributes.map(_.id))(attributeManagementService.getAttribute)
      verified   <- Future.traverse(agreementAttributes.toSeq.map(_.toString))(attributeManagementService.getAttribute)
      attributes <- AttributeManagementService.getAttributes(
        verified = verified,
        declared = declared,
        certified = certified
      )
    } yield attributes

    onComplete(result) {
      case Success(res) => getAttributesByConsumerId200(res)
      case Failure(ex)  =>
        logger.error(s"Error while getting consumer $consumerId attributes", ex)
        val errorResponse: Problem = problemOf(StatusCodes.BadRequest, RetrieveAttributesError(consumerId))
        getAttributesByConsumerId400(errorResponse)
    }
  }

}
