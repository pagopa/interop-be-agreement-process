package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AgreementManagementService {

  def createAgreement(producerId: UUID, consumerId: UUID, eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def getAgreementById(agreementId: String)(implicit contexts: Seq[(String, String)]): Future[Agreement]

  def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    states: List[AgreementState] = Nil,
    attributeId: Option[String] = None
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]]

  def upgradeById(agreementId: UUID, seed: UpgradeAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
