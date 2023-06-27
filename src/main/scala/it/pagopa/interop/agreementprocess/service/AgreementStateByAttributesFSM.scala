package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.model.EServiceDescriptor
import it.pagopa.interop.tenantmanagement.client.model.Tenant

object AgreementStateByAttributesFSM {

  def nextState(agreement: Agreement, descriptor: EServiceDescriptor, consumer: Tenant): AgreementState =
    agreement.state match {
      case DRAFT                        =>
        // Skip attributes validation if consuming own EServices
        if (agreement.consumerId == agreement.producerId) ACTIVE
        else if (!certifiedAttributesSatisfied(descriptor, consumer)) MISSING_CERTIFIED_ATTRIBUTES
        else if (
          descriptor.agreementApprovalPolicy == AUTOMATIC &&
          declaredAttributesSatisfied(descriptor, consumer) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumer)
        ) ACTIVE
        else if (declaredAttributesSatisfied(descriptor, consumer)) PENDING
        else DRAFT
      case PENDING                      =>
        if (!certifiedAttributesSatisfied(descriptor, consumer)) MISSING_CERTIFIED_ATTRIBUTES
        else if (!declaredAttributesSatisfied(descriptor, consumer)) DRAFT
        else if (!verifiedAttributesSatisfied(agreement, descriptor, consumer)) PENDING
        else ACTIVE
      case ACTIVE                       =>
        if (agreement.consumerId == agreement.producerId) ACTIVE
        else if (
          certifiedAttributesSatisfied(descriptor, consumer) &&
          declaredAttributesSatisfied(descriptor, consumer) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumer)
        )
          ACTIVE
        else
          SUSPENDED
      case SUSPENDED                    =>
        if (agreement.consumerId == agreement.producerId) ACTIVE
        else if (
          certifiedAttributesSatisfied(descriptor, consumer) &&
          declaredAttributesSatisfied(descriptor, consumer) &&
          verifiedAttributesSatisfied(agreement, descriptor, consumer)
        )
          ACTIVE
        else
          SUSPENDED
      case ARCHIVED                     => ARCHIVED
      case MISSING_CERTIFIED_ATTRIBUTES =>
        if (certifiedAttributesSatisfied(descriptor, consumer)) DRAFT
        else MISSING_CERTIFIED_ATTRIBUTES
      case REJECTED                     => REJECTED
    }

}
