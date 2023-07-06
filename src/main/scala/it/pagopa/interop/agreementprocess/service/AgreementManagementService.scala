package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementmanagement.model.agreement.{
  PersistentAgreementDocument,
  PersistentAgreement,
  PersistentAgreementState
}

trait AgreementManagementService {

  def createAgreement(seed: AgreementSeed)(implicit contexts: Seq[(String, String)]): Future[Agreement]

  def getAgreementById(
    agreementId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAgreement]

  def getAgreements(
    producerId: Option[UUID] = None,
    consumerId: Option[UUID] = None,
    eserviceId: Option[UUID] = None,
    descriptorId: Option[UUID] = None,
    states: Seq[PersistentAgreementState] = Nil,
    attributeId: Option[UUID] = None
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]]

  def upgradeById(agreementId: UUID, seed: UpgradeAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def addAgreementContract(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document]

  def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def addConsumerDocument(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document]

  def getConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PersistentAgreementDocument]

  def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
}
