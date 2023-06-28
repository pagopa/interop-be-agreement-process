package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.agreementprocess.common.readmodel.ReadModelAgreementQueries
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{DocumentNotFound, AgreementNotFound}
import it.pagopa.interop.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.utils.TypeConversions._
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

  override def getAgreementById(
    agreementId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAgreement] = for {
    persistentAgreement <- ReadModelAgreementQueries
      .getAgreementById(agreementId)(ec, readModel)
    agreement           <- persistentAgreement.toFuture(AgreementNotFound(agreementId.toString()))
  } yield agreement

  override def createAgreement(seed: AgreementSeed)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Agreement] = api.addAgreement(correlationId, seed, ip)(BearerToken(bearerToken))
      invoker.invoke(request, "Creating agreement")
    }

  override def getAgreements(
    producerId: Option[UUID],
    consumerId: Option[UUID],
    eserviceId: Option[UUID],
    descriptorId: Option[UUID],
    agreementStates: Seq[PersistentAgreementState],
    attributeId: Option[UUID]
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] =
    getAllAgreements(producerId, consumerId, eserviceId, descriptorId, agreementStates, attributeId)

  private def getAgreements(
    producerId: Option[UUID],
    consumerId: Option[UUID],
    eserviceId: Option[UUID],
    descriptorId: Option[UUID],
    agreementStates: Seq[PersistentAgreementState],
    attributeId: Option[UUID],
    offset: Int,
    limit: Int
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] =
    ReadModelAgreementQueries.getAgreements(
      producerId,
      consumerId,
      eserviceId,
      descriptorId,
      agreementStates,
      attributeId,
      offset,
      limit
    )

  private def getAllAgreements(
    producerId: Option[UUID],
    consumerId: Option[UUID],
    eserviceId: Option[UUID],
    descriptorId: Option[UUID],
    agreementStates: Seq[PersistentAgreementState],
    attributeId: Option[UUID]
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] = {

    def getAgreementsFrom(offset: Int): Future[Seq[PersistentAgreement]] =
      getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        agreementStates = agreementStates,
        attributeId = attributeId,
        offset = offset,
        limit = 50
      )

    def go(start: Int)(as: Seq[PersistentAgreement]): Future[Seq[PersistentAgreement]] =
      getAgreementsFrom(start).flatMap(esec =>
        if (esec.size < 50) Future.successful(as ++ esec) else go(start + 50)(as ++ esec)
      )

    go(0)(Nil)
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

  def addConsumerDocument(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request = api.addAgreementConsumerDocument(correlationId, agreementId, seed, ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Adding document to agreement = ${agreementId.toString()}")
    }

  override def getConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PersistentAgreementDocument] = for {
    persistentAgreement <- ReadModelAgreementQueries
      .getAgreementById(agreementId)(ec, readModel)
    agreement           <- persistentAgreement.toFuture(AgreementNotFound(agreementId.toString()))
    document            <- agreement.consumerDocuments
      .find(_.id == documentId)
      .toFuture(DocumentNotFound(agreementId.toString, documentId.toString))
  } yield document

  override def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request =
      api.removeAgreementConsumerDocument(correlationId, agreementId, documentId, ip)(BearerToken(bearerToken))

    invoker
      .invoke(request, s"Removing document = ${documentId.toString()} from agreement = ${agreementId.toString()}")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 =>
          Future.failed(DocumentNotFound(agreementId.toString(), documentId.toString()))
      }
  }
}
