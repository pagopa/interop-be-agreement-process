package it.pagopa.interop.agreementprocess.error

import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.catalogmanagement.client.model.EServiceDescriptorState
import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object AgreementProcessErrors {

  final case class UnexpectedError(message: String) extends ComponentError("0000", s"Unexpected error: $message")

  final case class MissingCertifiedAttributes(eServiceId: UUID, consumerId: UUID)
      extends ComponentError(
        "0001",
        s"Required certified attribute is missing. EService $eServiceId, Consumer: $consumerId"
      )

  final case class MissingDeclaredAttributes(eServiceId: UUID, consumerId: UUID)
      extends ComponentError(
        "0002",
        s"Required declared attribute is missing. EService $eServiceId, Consumer: $consumerId"
      )

  final case class AgreementNotInExpectedState(agreementId: String, state: AgreementManagement.AgreementState)
      extends ComponentError("0003", s"Agreement $agreementId not in expected state (current state: ${state.toString})")

  final case class DescriptorNotInExpectedState(
    eServiceId: UUID,
    descriptorId: UUID,
    allowedStates: List[EServiceDescriptorState]
  ) extends ComponentError(
        "0004",
        s"Descriptor $descriptorId of EService $eServiceId has not status in ${allowedStates.mkString("[", ",", "]")}"
      )

  final case class EServiceNotFound(eServiceId: UUID)
      extends ComponentError("0005", s"EService $eServiceId does not exist")

  final case class SubmitAgreementError(agreementId: String)
      extends ComponentError("0006", s"Error while submitting agreement $agreementId")

  final case class OperationNotAllowed(requesterId: UUID)
      extends ComponentError("0007", s"Operation not allowed by $requesterId")

  final case class AgreementActivationFailed(agreementId: UUID)
      extends ComponentError(
        "0008",
        s"Unable to activate agreement. Please check if attributes requirements are satisfied and suspension flags are clear"
      )

  final case class AgreementNotFound(agreementId: String)
      extends ComponentError("0009", s"Agreement $agreementId not found")

  final case class AgreementAlreadyExists(producerId: UUID, consumerId: UUID, eServiceId: UUID, descriptorId: UUID)
      extends ComponentError(
        "0010",
        s"Agreement already exists for Producer = $producerId, Consumer = $consumerId, EService = $eServiceId, Descriptor = $descriptorId"
      )

  final case class NoNewerDescriptorExists(eServiceId: UUID, currentDescriptorId: UUID)
      extends ComponentError(
        "0011",
        s"No newer descriptor in EService $eServiceId exists for upgrade. Current descriptor: $currentDescriptorId"
      )

  final case class PublishedDescriptorNotFound(eServiceId: UUID)
      extends ComponentError("0012", s"Published Descriptor not found in EService $eServiceId")

  final case class UnexpectedVersionFormat(eServiceId: UUID, descriptorId: UUID)
      extends ComponentError("0013", s"Version is not an Int for Descriptor $descriptorId of EService $eServiceId")

  final case class DescriptorNotFound(eServiceId: UUID, descriptorId: UUID)
      extends ComponentError("0014", s"Descriptor $descriptorId not found in EService $eServiceId")

  final case class StampNotFound(stamp: String) extends ComponentError("0015", s"Agreement stamp $stamp not found")

  final case class MissingUserInfo(userId: UUID)
      extends ComponentError("0016", s"Some mandatory info are missing for user ${user.toString()}")

  final case class ContractNotFound(agreementId: String)
      extends ComponentError("0017", s"Contract not found for agreement $agreementId")

}
