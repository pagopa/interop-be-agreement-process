package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.error.AgreementNotFound
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.AgreementPayload
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.ToString"
  )
)
final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def markVerifiedAttribute(
    bearerToken: String
  )(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed): Future[Agreement] = {

    val request: ApiRequest[Agreement] =
      api.updateAgreementVerifiedAttribute(agreementId, verifiedAttributeSeed)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Attribute verified! agreement ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error("Attribute verification FAILED", ex)
        Future.failed[Agreement](ex)
      }
  }

  override def activateById(
    bearerToken: String
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    val request: ApiRequest[Agreement] =
      api.activateAgreement(agreementId, stateChangeDetails)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Activating agreement ${x.code}")
        logger.info(s"Activating agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error("Activating agreement FAILED", ex)
        Future.failed[Agreement](ex)
      }
  }

  override def suspendById(
    bearerToken: String
  )(agreementId: String, stateChangeDetails: StateChangeDetails): Future[Agreement] = {
    val request: ApiRequest[Agreement] =
      api.suspendAgreement(agreementId, stateChangeDetails)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Suspending agreement ${x.code}")
        logger.info(s"Suspending agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error("Suspending agreement FAILED", ex)
        Future.failed[Agreement](ex)
      }
  }

  override def upgradeById(bearerToken: String)(agreementId: UUID, agreementSeed: AgreementSeed): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.upgradeAgreementById(agreementId, agreementSeed)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Agreement upgraded ${x.code}")
        logger.info(s"Agreement upgraded ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error("Agreement upgrade FAILED", ex)
        Future.failed[Agreement](ex)
      }
  }

  override def getAgreementById(bearerToken: String)(agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Retrieving agreement ${x.code}")
        logger.info(s"Retrieving agreement ${x.content}")
        x.content
      }
      .recoverWith {
        case ex: ApiError[_] if ex.code == 404 =>
          logger.error("Retrieving agreement FAILED", ex)
          Future.failed[Agreement](AgreementNotFound(agreementId))
        case ex: ApiError[_] =>
          logger.error("Retrieving agreement FAILED", ex)
          Future.failed[Agreement](ex)
      }
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
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Retrieving agreement ${x.code}")
        logger.info(s"Retrieving agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error("Retrieving agreement FAILED", ex)
        Future.failed[Agreement](ex)
      }
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

    invoker
      .execute[Seq[Agreement]](request)
      .map { x =>
        logger.info(s"Retrieving agreements ${x.code}")
        logger.info(s"Retrieving agreements ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error("Retrieving agreements FAILED", ex)
        Future.failed[Seq[Agreement]](ex)
      }
  }

}
