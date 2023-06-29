package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
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
    consumer: PersistentTenant
  ): PersistentAgreementState =
    agreement.state match {
      case Draft                      =>
        // Skip attributes validation if consuming own EServices
        if (agreement.consumerId == agreement.producerId) Active
        else if (!certifiedAttributesSatisfied(descriptor, consumer)) MissingCertifiedAttributes
        else if (
          descriptor.agreementApprovalPolicy == Some(Automatic) &&
          declaredAttributesSatisfied(descriptor, consumer) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumer)
        ) Active
        else if (declaredAttributesSatisfied(descriptor, consumer)) Pending
        else Draft
      case Pending                    =>
        if (!certifiedAttributesSatisfied(descriptor, consumer)) MissingCertifiedAttributes
        else if (!declaredAttributesSatisfied(descriptor, consumer)) Draft
        else if (!verifiedAttributesSatisfied(agreement, descriptor, consumer)) Pending
        else Active
      case Active                     =>
        if (agreement.consumerId == agreement.producerId) Active
        else if (
          certifiedAttributesSatisfied(descriptor, consumer) &&
          declaredAttributesSatisfied(descriptor, consumer) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumer)
        )
          Active
        else
          Suspended
      case Suspended                  =>
        if (agreement.consumerId == agreement.producerId) Active
        else if (
          certifiedAttributesSatisfied(descriptor, consumer) &&
          declaredAttributesSatisfied(descriptor, consumer) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumer)
        )
          Active
        else
          Suspended
      case Archived                   => Archived
      case MissingCertifiedAttributes =>
        if (certifiedAttributesSatisfied(descriptor, consumer)) Draft
        else MissingCertifiedAttributes
      case Rejected                   => Rejected
    }

}
