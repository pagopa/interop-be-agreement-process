package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.AgreementPayload
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import org.slf4j.{Logger, LoggerFactory}

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
    bearerToken: String,
    agreementId: String,
    verifiedAttributeSeed: VerifiedAttributeSeed
  ): Future[Agreement] = {

    val request: ApiRequest[Agreement] =
      api.updateAgreementVerifiedAttribute(agreementId, verifiedAttributeSeed)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Attribute verified! agreement ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Attribute verification FAILED: ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def activateById(bearerToken: String, agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.activateAgreement(agreementId)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Activating agreement ${x.code}")
        logger.info(s"Activating agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Activating agreement FAILED: ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

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
        logger.error(s"Retrieving agreement ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def createAgreement(
    bearerToken: String,
    agreementPayload: AgreementPayload,
    verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
  ): Future[Agreement] = {

    val seed: AgreementSeed = AgreementSeed(
      eserviceId = agreementPayload.eserviceId,
      producerId = agreementPayload.producerId,
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
        logger.error(s"Retrieving agreement ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

//  override def validatePayload(bearerToken: String, payload: AgreementPayload): Future[AgreementPayload] = {
//    val request: ApiRequest[Seq[Agreement]] =
//      api.getAgreements(
//        producerId = Some(payload.producerId.toString),
//        consumerId = Some(payload.consumerId.toString),
//        eserviceId = Some(payload.eserviceId.toString),
//        status = Some(AgreementEnums.Status.Active.toString)
//      )(BearerToken(bearerToken))
//    invoker
//      .execute[Seq[Agreement]](request)
//      .flatMap(agreements =>
//        Future.fromTry(
//          Either
//            .cond(
//              agreements.content.isEmpty,
//              payload,
//              new RuntimeException(
//                s"Producer ${payload.producerId} already has an active agreement for ${payload.consumerId}"
//              )
//            )
//            .toTry
//        )
//      )
//      .recoverWith { case ex =>
//        logger.error(s"Check active agreements failed ${ex.getMessage}")
//        Future.failed[AgreementPayload](ex)
//      }
//  }

  override def getAgreements(
    bearerToken: String,
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    status: Option[String] = None
  ): Future[Seq[Agreement]] = {

    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(producerId = producerId, consumerId = consumerId, eserviceId = eserviceId, status = status)(
        BearerToken(bearerToken)
      )

    invoker
      .execute[Seq[Agreement]](request)
      .map { x =>
        logger.info(s"Retrieving agreements ${x.code}")
        logger.info(s"Retrieving agreements ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving agreements ${ex.getMessage}")
        Future.failed[Seq[Agreement]](ex)
      }
  }

}
