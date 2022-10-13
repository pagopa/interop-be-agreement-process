package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.AgreementNotFound
import it.pagopa.interop.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def upgradeById(agreementId: UUID, seed: UpgradeAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = withHeaders { (bearerToken, correlationId, ip) =>
    val request = api.upgradeAgreementById(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
    invoker.invoke(request, s"Upgrading agreement by id = $agreementId")
  }

  override def getAgreementById(agreementId: String)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.getAgreement(correlationId, agreementId, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving agreement by id = $agreementId").recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(AgreementNotFound(agreementId))
      }
    }

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

    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Agreement] = api.addAgreement(correlationId, seed, ip)(BearerToken(bearerToken))
      invoker.invoke(request, "Creating agreement")
    }
  }

  override def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    states: List[AgreementState] = Nil,
    attributeId: Option[String] = None
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Seq[Agreement]] = api.getAgreements(
        correlationId,
        ip,
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        attributeId = attributeId,
        states = states
      )(BearerToken(bearerToken))
      invoker.invoke(request, "Retrieving agreements")
    }

  override def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Agreement] =
      api.updateAgreementById(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
    invoker.invoke(request, s"Updating agreement with id = $agreementId")
  }

  override def addAgreementContract(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Document] =
      api.addAgreementContract(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
    invoker.invoke(request, s"Adding  agreement with id = $agreementId")
  }

  override def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.deleteAgreement(correlationId, agreementId.toString, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Deleting agreement by id = $agreementId")
    }

  def addConsumerDocuments(agreementId: UUID, seed: DocumentSeed)(implicit contexts: Seq[(String, String)]): Future[Document] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.addAgreementConsumerDocument(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Adding document to agreement = $agreementId")
    }
}
