package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.tenantmanagement.client.model.Tenant

object AgreementStateByAttributesFSM {

  def nextState(agreement: Agreement, eService: EService, consumer: Tenant): AgreementState =
    agreement.state match {
      case DRAFT                        =>
        // Skip attributes validation if consuming own EServices
        if (agreement.consumerId == agreement.producerId) ACTIVE
        else if (!certifiedAttributesSatisfied(eService, consumer)) MISSING_CERTIFIED_ATTRIBUTES
        else if (
          eService.descriptors.exists(d => d.id == agreement.descriptorId && d.agreementApprovalPolicy == AUTOMATIC) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(agreement, eService, consumer)
        ) ACTIVE
        else if (declaredAttributesSatisfied(eService, consumer)) PENDING
        else DRAFT
      case PENDING                      =>
        if (!certifiedAttributesSatisfied(eService, consumer)) MISSING_CERTIFIED_ATTRIBUTES
        else if (!declaredAttributesSatisfied(eService, consumer)) DRAFT
        else if (!verifiedAttributesSatisfied(agreement, eService, consumer)) PENDING
        else ACTIVE
      case ACTIVE                       =>
        if (agreement.consumerId == agreement.producerId) ACTIVE
        else if (
          certifiedAttributesSatisfied(eService, consumer) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(agreement, eService, consumer)
        )
          ACTIVE
        else
          SUSPENDED
      case SUSPENDED                    =>
        if (agreement.consumerId == agreement.producerId) ACTIVE
        else if (
          certifiedAttributesSatisfied(eService, consumer) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(agreement, eService, consumer)
        )
          ACTIVE
        else
          SUSPENDED
      case ARCHIVED                     => ARCHIVED
      case MISSING_CERTIFIED_ATTRIBUTES =>
        if (certifiedAttributesSatisfied(eService, consumer)) DRAFT
        else MISSING_CERTIFIED_ATTRIBUTES
      case REJECTED                     => REJECTED
    }

}
