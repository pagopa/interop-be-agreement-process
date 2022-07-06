package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.BearerToken
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def markVerifiedAttribute(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.updateAgreementVerifiedAttribute(correlationId, agreementId, verifiedAttributeSeed, ip)(
      BearerToken(bearerToken)
    )
    result <- invoker.invoke(request, s"Verifying attributes for agreement $agreementId")
  } yield result

  override def addAgreementDocument(agreementId: String, agreementDocumentSeed: AgreementDocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.addAgreementDocument(correlationId, agreementId, agreementDocumentSeed, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Adding document to agreement id $agreementId")
  } yield result

  override def activateById(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.activateAgreement(correlationId, agreementId, stateChangeDetails, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Activating agreement by id = $agreementId")
  } yield result

  override def suspendById(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.suspendAgreement(correlationId, agreementId, stateChangeDetails, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Suspending agreement by id = $agreementId")
  } yield result

  override def upgradeById(agreementId: UUID, agreementSeed: AgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.upgradeAgreementById(correlationId, agreementId, agreementSeed, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Updating agreement by id = $agreementId")
  } yield result

  override def getAgreementById(agreementId: String)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreement(correlationId, agreementId, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Retrieving agreement by id = $agreementId")
    } yield result

  override def createAgreement(
    producerId: UUID,
    agreementPayload: AgreementPayload,
    verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
  )(implicit contexts: Seq[(String, String)]): Future[Agreement] = {
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

  override def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = for {
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
