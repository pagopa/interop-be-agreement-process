package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.ProcessApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Audience, Problem}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementService, CatalogManagementService}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Recursion"
  )
)
class ProcessApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService
)(implicit ec: ExecutionContext)
    extends ProcessApiService {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /** Code: 200, Message: audiences found, DataType: Audience
    * Code: 400, Message: Invalid ID supplied, DataType: Problem
    */
  override def getAudienceByAgreementId(agreementId: String)(implicit
    toEntityMarshallerAudience: ToEntityMarshaller[Audience],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val bearerToken = contexts.map(_._2)(0)
    logger.info(s"Getting audience for agreement $agreementId")
    val result = for {
      agreement       <- agreementManagementService.getAgreementById(bearerToken, agreementId)
      activeAgreement <- agreementManagementService.checkAgreementActivation(agreement)
      eservice        <- catalogManagementService.getEServiceById(bearerToken, activeAgreement.eserviceId.toString)
      activeEservice  <- catalogManagementService.checkEServiceActivation(eservice)
    } yield Audience(activeEservice.name, List.empty)

    onComplete(result) {
      case Success(res) => getAudienceByAgreementId200(res)
      case Failure(ex) =>
        val errorResponse: Problem =
          Problem(Option(ex.getMessage), 400, s"error while retrieving audience for agreement $agreementId")
        getAudienceByAgreementId400(errorResponse)
    }
  }
}
