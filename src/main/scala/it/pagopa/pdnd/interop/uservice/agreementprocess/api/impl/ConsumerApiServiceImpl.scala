package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.commons.jwt.service.JWTReader
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.ConsumerApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Attributes, Problem}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.ToString"))
class ConsumerApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  partyManagementService: PartyManagementService,
  attributeManagementService: AttributeManagementService,
  jwtReader: JWTReader
)(implicit ec: ExecutionContext)
    extends ConsumerApiService {

  /** Code: 200, Message: attributes found, DataType: Attributes
    * Code: 404, Message: Consumer not found, DataType: Problem
    * Code: 400, Message: Invalid ID supplied, DataType: Problem
    */
  override def getAttributesByConsumerId(consumerId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributes: ToEntityMarshaller[Attributes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Attributes] = for {
      bearerToken <- validateBearer(contexts, jwtReader)
      agreements <- agreementManagementService.getAgreements(bearerToken)(
        consumerId = Some(consumerId),
        state = Some(AgreementManagementDependency.AgreementState.ACTIVE)
      )
      eserviceIds = agreements.map(_.eserviceId)
      eservices       <- Future.traverse(eserviceIds)(catalogManagementService.getEServiceById(bearerToken))
      consumerUuid    <- consumerId.toFutureUUID
      partyAttributes <- partyManagementService.getPartyAttributes(bearerToken)(consumerUuid)
      eserviceAttributes <- eservices
        .flatTraverse(eservice => CatalogManagementService.flattenAttributes(eservice.attributes.declared))
      agreementAttributes <- AgreementManagementService.extractVerifiedAttribute(agreements)
      certified           <- Future.traverse(partyAttributes)(attributeManagementService.getAttribute(bearerToken))
      declared            <- Future.traverse(eserviceAttributes.map(_.id))(attributeManagementService.getAttribute(bearerToken))
      verified <- Future.traverse(agreementAttributes.toSeq.map(_.toString))(
        attributeManagementService.getAttribute(bearerToken)
      )
      attributes <- AttributeManagementService.getAttributes(
        verified = verified,
        declared = declared,
        certified = certified
      )
    } yield attributes

    onComplete(result) {
      case Success(res) => getAttributesByConsumerId200(res)
      case Failure(ex) =>
        val errorResponse: Problem = {
          problemOf(StatusCodes.BadRequest, "0001", ex, s"Error while retrieving attributes for consumer $consumerId")
        }
        getAttributesByConsumerId400(errorResponse)
    }
  }

}
