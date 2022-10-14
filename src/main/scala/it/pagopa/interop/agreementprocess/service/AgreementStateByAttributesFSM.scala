package it.pagopa.interop.agreementprocess.service

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.model.{Attribute, EService}
import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID

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

  def certifiedAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    attributesSatisfied(
      eService.attributes.certified,
      consumer.attributes.mapFilter(_.certified).filter(_.revocationTimestamp.isEmpty).map(_.id)
    )

  def declaredAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    attributesSatisfied(
      eService.attributes.declared,
      consumer.attributes.mapFilter(_.declared).filter(_.revocationTimestamp.isEmpty).map(_.id)
    )

  def verifiedAttributesSatisfied(agreement: Agreement, eService: EService, consumer: Tenant): Boolean =
    attributesSatisfied(
      eService.attributes.verified,
      consumer.attributes.mapFilter(_.verified).filter(_.verifiedBy.exists(_.id == agreement.producerId)).map(_.id)
    )

  private def attributesSatisfied(requested: Seq[Attribute], assigned: Seq[UUID]): Boolean =
    requested.forall {
      case Attribute(Some(single), _) => assigned.contains(single.id)
      case Attribute(_, Some(group))  => group.map(_.id).intersect(assigned).nonEmpty
      case _                          => true
    }
}
