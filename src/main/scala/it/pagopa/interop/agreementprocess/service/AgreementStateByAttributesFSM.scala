package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenantAttribute
import it.pagopa.interop.catalogmanagement.model.CatalogDescriptor
import it.pagopa.interop.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentAgreementState,
  Draft,
  Active,
  MissingCertifiedAttributes,
  Pending,
  Suspended,
  Rejected,
  Archived
}
import it.pagopa.interop.catalogmanagement.model.Automatic

object AgreementStateByAttributesFSM {

  def nextState(
    agreement: PersistentAgreement,
    descriptor: CatalogDescriptor,
    consumerAttributes: List[PersistentTenantAttribute]
  ): PersistentAgreementState =
    agreement.state match {
      case Draft                      =>
        // Skip attributes validation if consuming own EServices
        if (agreement.consumerId == agreement.producerId) Active
        else if (!certifiedAttributesSatisfied(descriptor, consumerAttributes)) MissingCertifiedAttributes
        else if (
          descriptor.agreementApprovalPolicy.contains(Automatic) &&
          declaredAttributesSatisfied(descriptor, consumerAttributes) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumerAttributes)
        ) Active
        else if (declaredAttributesSatisfied(descriptor, consumerAttributes)) Pending
        else Draft
      case Pending                    =>
        if (!certifiedAttributesSatisfied(descriptor, consumerAttributes)) MissingCertifiedAttributes
        else if (!declaredAttributesSatisfied(descriptor, consumerAttributes)) Draft
        else if (!verifiedAttributesSatisfied(agreement, descriptor, consumerAttributes)) Pending
        else Active
      case Active                     =>
        if (agreement.consumerId == agreement.producerId) Active
        else if (
          certifiedAttributesSatisfied(descriptor, consumerAttributes) &&
          declaredAttributesSatisfied(descriptor, consumerAttributes) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumerAttributes)
        )
          Active
        else
          Suspended
      case Suspended                  =>
        if (agreement.consumerId == agreement.producerId) Active
        else if (
          certifiedAttributesSatisfied(descriptor, consumerAttributes) &&
          declaredAttributesSatisfied(descriptor, consumerAttributes) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumerAttributes)
        )
          Active
        else
          Suspended
      case Archived                   => Archived
      case MissingCertifiedAttributes =>
        if (certifiedAttributesSatisfied(descriptor, consumerAttributes)) Draft
        else MissingCertifiedAttributes
      case Rejected                   => Rejected
    }

}
