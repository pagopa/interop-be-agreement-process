package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors.{ResourceConflictError, ResourceNotFoundError}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, partyApi: PartyApi)
    extends PartyManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getPartyAttributes(bearerToken: String)(partyId: UUID): Future[Seq[String]] = {
    logger.info(s"TODO > Bearer Token should be used $bearerToken") //TODO pass bearer token
    val request: ApiRequest[Seq[String]] = partyApi.getPartyAttributes(partyId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving Attributes of party $partyId", invocationRecovery(partyId.toString))
  }

  override def getOrganization(bearerToken: String)(partyId: UUID): Future[Organization] = {
    val request: ApiRequest[Organization] = partyApi.getOrganizationById(partyId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving Organization of party $partyId", invocationRecovery(partyId.toString))
  }

  private def invocationRecovery[T](resourceId: String): (Logger, String) => PartialFunction[Throwable, Future[T]] =
    (logger, message) => {
      case ApiError(code, apiMessage, _, _, _) if code == 409 =>
        logger.error(s"$apiMessage. code > $code - message > $message")
        Future.failed[T](ResourceConflictError(resourceId))
      case ApiError(code, apiMessage, _, _, _) if code == 404 =>
        logger.error(s"$apiMessage. code > $code - message > $message")
        Future.failed[T](ResourceNotFoundError(resourceId))
      case ApiError(code, apiMessage, _, _, _) =>
        logger.error(s"$apiMessage. code > $code - message > $message")
        Future.failed[T](new RuntimeException(message))
      case ex =>
        logger.error(s"Error: ${ex.getMessage}")
        Future.failed[T](ex)
    }

}
