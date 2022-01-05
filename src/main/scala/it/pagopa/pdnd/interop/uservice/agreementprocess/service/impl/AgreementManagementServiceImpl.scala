package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.commons.utils.errors.{ResourceConflictError, ResourceNotFoundError}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.AgreementPayload
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)
    extends AgreementManagementService {

  private final val emptyString = ""

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def markVerifiedAttribute(
    bearerToken: String
  )(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed): Future[Agreement] = {

    val request: ApiRequest[Agreement] =
      api.updateAgreementVerifiedAttribute(agreementId, verifiedAttributeSeed)(BearerToken(bearerToken))
    invoker.invoke(request, s"Verifying attributes for agreement $agreementId", invocationRecovery(agreementId))
  }

  override def activateById(
    bearerToken: String
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    val request: ApiRequest[Agreement] =
      api.activateAgreement(agreementId, stateChangeDetails)(BearerToken(bearerToken))
    invoker.invoke(request, s"Activating agreement by id = $agreementId", invocationRecovery(agreementId))
  }

  override def suspendById(
    bearerToken: String
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    val request: ApiRequest[Agreement] =
      api.suspendAgreement(agreementId, stateChangeDetails)(BearerToken(bearerToken))
    invoker.invoke(request, s"Suspending agreement by id = $agreementId", invocationRecovery(agreementId))
  }

  override def upgradeById(bearerToken: String)(agreementId: UUID, agreementSeed: AgreementSeed): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.upgradeAgreementById(agreementId, agreementSeed)(BearerToken(bearerToken))

    val resourceId: String =
      s"agreementId=${agreementId.toString}/consumerId=${agreementSeed.consumerId}/" +
        s"eserviceId=${agreementSeed.eserviceId}/descriptorId=${agreementSeed.descriptorId}"

    invoker.invoke(request, s"Updating agreement by id = $agreementId", invocationRecovery(resourceId))
  }

  override def getAgreementById(bearerToken: String)(agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving agreement by id = $agreementId", invocationRecovery(agreementId))
  }

  override def createAgreement(bearerToken: String)(
    producerId: UUID,
    agreementPayload: AgreementPayload,
    verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
  ): Future[Agreement] = {

    val seed: AgreementSeed = AgreementSeed(
      eserviceId = agreementPayload.eserviceId,
      descriptorId = agreementPayload.descriptorId,
      producerId = producerId,
      consumerId = agreementPayload.consumerId,
      verifiedAttributes = verifiedAttributeSeeds
    )

    val request: ApiRequest[Agreement] = api.addAgreement(seed)(BearerToken(bearerToken))

    val resourceId: String =
      s"producerId=${producerId.toString}/consumerId=${agreementPayload.consumerId}/" +
        s"eserviceId=${agreementPayload.eserviceId}/descriptorId=${agreementPayload.descriptorId}"

    invoker.invoke(request, "Creating agreement", invocationRecovery(resourceId))
  }

  override def getAgreements(bearerToken: String)(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  ): Future[Seq[Agreement]] = {

    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = state
      )(BearerToken(bearerToken))

    val resourceId: String =
      s"producerId=${producerId.getOrElse(emptyString)}/consumerId=${consumerId.getOrElse(emptyString)}/" +
        s"eserviceId=${eserviceId.getOrElse(emptyString)}/descriptorId=${descriptorId.getOrElse(emptyString)}/" +
        s"state=${state.map(_.toString).getOrElse(emptyString)}"

    invoker.invoke(request, "Retrieving agreements", invocationRecovery(resourceId))
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
