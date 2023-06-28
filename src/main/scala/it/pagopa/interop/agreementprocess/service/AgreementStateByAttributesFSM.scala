package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.catalogmanagement.model.Automatic

object AgreementStateByAttributesFSM {

  def nextState(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    consumer: PersistentTenant
  ): AgreementManagement.AgreementState =
    agreement.state match {
      case Draft                      =>
        // Skip attributes validation if consuming own EServices
        if (agreement.consumerId == agreement.producerId) AgreementManagement.AgreementState.ACTIVE
        else if (!certifiedAttributesSatisfied(eService, consumer))
          AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
        else if (
          eService.descriptors.exists(d => d.id == agreement.descriptorId && d.agreementApprovalPolicy == Automatic) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(agreement, eService, consumer)
        ) AgreementManagement.AgreementState.ACTIVE
        else if (declaredAttributesSatisfied(eService, consumer)) AgreementManagement.AgreementState.PENDING
        else AgreementManagement.AgreementState.DRAFT
      case Pending                    =>
        if (!certifiedAttributesSatisfied(eService, consumer))
          AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
        else if (!declaredAttributesSatisfied(eService, consumer)) AgreementManagement.AgreementState.DRAFT
        else if (!verifiedAttributesSatisfied(agreement, eService, consumer)) AgreementManagement.AgreementState.PENDING
        else AgreementManagement.AgreementState.ACTIVE
      case Active                     =>
        if (agreement.consumerId == agreement.producerId) AgreementManagement.AgreementState.ACTIVE
        else if (
          certifiedAttributesSatisfied(eService, consumer) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(agreement, eService, consumer)
        )
          AgreementManagement.AgreementState.ACTIVE
        else
          AgreementManagement.AgreementState.SUSPENDED
      case Suspended                  =>
        if (agreement.consumerId == agreement.producerId) AgreementManagement.AgreementState.ACTIVE
        else if (
          certifiedAttributesSatisfied(eService, consumer) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(agreement, eService, consumer)
        )
          AgreementManagement.AgreementState.ACTIVE
        else
          AgreementManagement.AgreementState.SUSPENDED
      case Archived                   => AgreementManagement.AgreementState.ARCHIVED
      case MissingCertifiedAttributes =>
        if (certifiedAttributesSatisfied(eService, consumer)) AgreementManagement.AgreementState.DRAFT
        else AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case Rejected                   => AgreementManagement.AgreementState.REJECTED
    }

}
