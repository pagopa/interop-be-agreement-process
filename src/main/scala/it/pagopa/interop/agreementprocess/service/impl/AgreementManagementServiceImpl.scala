package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.BearerToken
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def markVerifiedAttribute(
    contexts: Seq[(String, String)]
  )(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed): Future[Agreement] = {

    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.updateAgreementVerifiedAttribute(correlationId, agreementId, verifiedAttributeSeed, ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Verifying attributes for agreement $agreementId")
    } yield result

  }

  override def activateById(
    contexts: Seq[(String, String)]
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.activateAgreement(correlationId, agreementId, stateChangeDetails, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Activating agreement by id = $agreementId")
    } yield result
  }

  override def suspendById(
    contexts: Seq[(String, String)]
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.suspendAgreement(correlationId, agreementId, stateChangeDetails, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Suspending agreement by id = $agreementId")
    } yield result
  }

  override def upgradeById(
    contexts: Seq[(String, String)]
  )(agreementId: UUID, agreementSeed: AgreementSeed): Future[Agreement] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.upgradeAgreementById(correlationId, agreementId, agreementSeed, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Updating agreement by id = $agreementId")
    } yield result
  }

  override def getAgreementById(contexts: Seq[(String, String)])(agreementId: String): Future[Agreement] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreement(correlationId, agreementId, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Retrieving agreement by id = $agreementId")
    } yield result
  }

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
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.addAgreement(correlationId, seed, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, "Creating agreement")
    } yield result
  }

  override def getAgreements(contexts: Seq[(String, String)])(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  ): Future[Seq[Agreement]] = {

    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreements(
        correlationId,
        ip,
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = state
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, "Retrieving agreements")
    } yield result

  }

}
