package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.BearerToken
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils.AkkaUtils.getFutureBearer
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def markVerifiedAttribute(
    contexts: Seq[(String, String)]
  )(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed): Future[Agreement] =
    for {
      bearer <- getFutureBearer(contexts)
      request = api.updateAgreementVerifiedAttribute(agreementId, verifiedAttributeSeed)(BearerToken(bearer))
      result <- invoker.invoke(request, s"Verifying attributes for agreement $agreementId", contexts)
    } yield result

  override def activateById(
    contexts: Seq[(String, String)]
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] =
    for {
      bearer <- getFutureBearer(contexts)
      request = api.activateAgreement(agreementId, stateChangeDetails)(BearerToken(bearer))
      result <- invoker.invoke(request, s"Activating agreement by id = $agreementId", contexts)
    } yield result

  override def suspendById(
    contexts: Seq[(String, String)]
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    for {
      bearer <- getFutureBearer(contexts)
      request = api.suspendAgreement(agreementId, stateChangeDetails)(BearerToken(bearer))
      result <- invoker.invoke(request, s"Suspending agreement by id = $agreementId", contexts)
    } yield result
  }

  override def upgradeById(
    contexts: Seq[(String, String)]
  )(agreementId: UUID, agreementSeed: AgreementSeed): Future[Agreement] =
    for {
      bearer <- getFutureBearer(contexts)
      request = api.upgradeAgreementById(agreementId, agreementSeed)(BearerToken(bearer))
      result <- invoker.invoke(request, s"Updating agreement by id = $agreementId", contexts)
    } yield result

  override def getAgreementById(contexts: Seq[(String, String)])(agreementId: String): Future[Agreement] =
    for {
      bearer <- getFutureBearer(contexts)
      request = api.getAgreement(agreementId)(BearerToken(bearer))
      result <- invoker.invoke(request, s"Retrieving agreement by id = $agreementId", contexts)
    } yield result

  override def createAgreement(contexts: Seq[(String, String)])(
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

    for {
      bearer <- getFutureBearer(contexts)
      request = api.addAgreement(seed)(BearerToken(bearer))
      result <- invoker.invoke(request, "Creating agreement", contexts)
    } yield result
  }

  override def getAgreements(contexts: Seq[(String, String)])(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  ): Future[Seq[Agreement]] =
    for {
      bearer <- getFutureBearer(contexts)
      request = api.getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = state
      )(BearerToken(bearer))
      result <- invoker.invoke(request, "Retrieving agreements", contexts)
    } yield result

}
