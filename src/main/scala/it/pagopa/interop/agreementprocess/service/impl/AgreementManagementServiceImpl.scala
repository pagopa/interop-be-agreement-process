package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.AgreementNotFound
import it.pagopa.interop.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def upgradeById(agreementId: UUID, seed: UpgradeAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.upgradeAgreementById(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Upgrading agreement by id = $agreementId")
  } yield result

  override def getAgreementById(agreementId: String)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreement(correlationId, agreementId, ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Retrieving agreement by id = $agreementId").recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(AgreementNotFound(agreementId))
      }
    } yield result

  override def createAgreement(producerId: UUID, consumerId: UUID, eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = {
    val seed: AgreementSeed = AgreementSeed(
      eserviceId = eServiceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      verifiedAttributes = Nil,
      certifiedAttributes = Nil,
      declaredAttributes = Nil
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
    states: List[AgreementState] = Nil
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAgreements(
      correlationId,
      ip,
      producerId = producerId,
      consumerId = consumerId,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      state = states.headOption // TODO This parameter should be a list. Remove headOption
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Retrieving agreements")
  } yield result

  override def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.updateAgreementById(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, s"Updating agreement with id = $agreementId")
  } yield result
}
