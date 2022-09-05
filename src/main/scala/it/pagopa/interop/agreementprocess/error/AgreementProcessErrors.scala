package it.pagopa.interop.agreementprocess.error

import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.catalogmanagement.client.model.EServiceDescriptorState
import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object AgreementProcessErrors {

  final case class UnexpectedError(message: String) extends ComponentError("0000", s"Unexpected error: $message")

  final case class MissingCertifiedAttributes(eServiceId: UUID, descriptorId: UUID, consumerId: UUID)
      extends ComponentError(
        "00xx",
        s"Required certificated attribute is missing. EService $eServiceId, Descriptor $descriptorId, Consumer: $consumerId"
      )

  final case class MissingDeclaredAttributes(eServiceId: UUID, descriptorId: UUID, consumerId: UUID)
      extends ComponentError(
        "00xx",
        s"Required declared attribute is missing. EService $eServiceId, Descriptor $descriptorId, Consumer: $consumerId"
      )

  final case class AgreementNotInExpectedState(agreementId: String, state: AgreementManagement.AgreementState)
      extends ComponentError("00xx", s"Agreement $agreementId not in expected state (current state: ${state.toString})")

  final case class DescriptorNotInExpectedState(
    eServiceId: UUID,
    descriptorId: UUID,
    allowedStates: List[EServiceDescriptorState]
  ) extends ComponentError(
        "00xx",
        s"Descriptor $descriptorId of EService $eServiceId has not status in ${allowedStates.mkString("[", ",", "]")}"
      )

  final case class EServiceNotFound(eServiceId: UUID)
      extends ComponentError("00xx", s"EService $eServiceId does not exist")

  final case class SubmitAgreementError(agreementId: String)
      extends ComponentError("00xx", s"Error while submitting agreement $agreementId")

  final case class OperationNotAllowed(requesterId: UUID)
      extends ComponentError("00xx", s"Operation not allowed by $requesterId")

  final case class AgreementActivationFailed(agreementId: UUID)
      extends ComponentError(
        "00xx",
        s"Unable to activate agreement. Please check if attributes requirements are satisfied and suspension flags are clear"
      )

  ////
  final case class ActivateAgreementError(agreementId: String)
      extends ComponentError("0001", s"Error while activating agreement $agreementId")

  final case class SuspendAgreementError(agreementId: String)
      extends ComponentError("0002", s"Error while suspending agreement $agreementId")

  final case class CreateAgreementError(agreementPayload: AgreementPayload)
      extends ComponentError("0003", s"Error while creating agreement $agreementPayload")

  final case object RetrieveAgreementsError
      extends ComponentError("0004", "Error while retrieving agreements with filters")

  final case class AgreementNotFound(agreementId: String)
      extends ComponentError("0005", s"Agreement $agreementId not found")

  final case class RetrieveAgreementError(agreementId: String)
      extends ComponentError("0006", s"Error while suspending agreement $agreementId")

  final case class VerifyAgreementAttributeError(agreementId: String, attributeId: String)
      extends ComponentError("0007", s"Error while verifying agreement $agreementId attribute $attributeId")

  final case class UpdateAgreementError(agreementId: String)
      extends ComponentError("0008", s"Error while updating agreement $agreementId")

  final case class AgreementAlreadyExists(producerId: UUID, consumerId: UUID, eServiceId: UUID, descriptorId: UUID)
      extends ComponentError(
        "0009",
        s"Agreement already exists for Producer = $producerId, Consumer = $consumerId, EService = $eServiceId, Descriptor = $descriptorId"
      )

  final case class AgreementAttributeNotFound(attributeId: String)
      extends ComponentError("0010", s"EService attribute $attributeId not found in agreement")

  final case class DescriptorNotFound(eserviceId: String, descriptorId: String)
      extends ComponentError("0011", s"Descriptor $descriptorId not found in eservice $eserviceId")

  final case class RetrieveAttributesError(consumerId: String)
      extends ComponentError("00012", s"Error while retrieving attributes for consumer $consumerId")

}
