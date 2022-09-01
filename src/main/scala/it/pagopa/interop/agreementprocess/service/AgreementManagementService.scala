package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.{model => AgreementProcess}

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
    states: List[AgreementState] = Nil
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]]

  def upgradeById(agreementId: UUID, seed: UpgradeAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
}

object AgreementManagementService {

  def agreementStateToApi(status: AgreementState): AgreementProcess.AgreementState = status match {
    case AgreementState.DRAFT                        => AgreementProcess.AgreementState.DRAFT
    case AgreementState.PENDING                      => AgreementProcess.AgreementState.PENDING
    case AgreementState.ACTIVE                       => AgreementProcess.AgreementState.ACTIVE
    case AgreementState.SUSPENDED                    => AgreementProcess.AgreementState.SUSPENDED
    case AgreementState.INACTIVE                     => AgreementProcess.AgreementState.INACTIVE
    case AgreementState.MISSING_CERTIFIED_ATTRIBUTES => AgreementProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
  }
}
