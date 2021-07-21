package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.ApiRequest
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementEnums}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.BearerToken
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.ToString"
  )
)
final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Retrieving agreement ${x.code}")
        logger.info(s"Retrieving agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving person ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  override def checkAgreementActivation(agreement: Agreement): Future[Agreement] = {
    Future.fromTry(
      Either
        .cond(
          agreement.status.equals(AgreementEnums.Status.Active),
          agreement,
          new RuntimeException(s"Agreement ${agreement.id} is not active")
        )
        .toTry
    )
  }
}
