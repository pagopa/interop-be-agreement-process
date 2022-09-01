package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementmanagement.client.model.AgreementState
import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.catalogmanagement.client.model.{Attribute, EService}
import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID

object AgreementStateByAttributesFSM {

  def nextState(currentState: AgreementState, eService: EService, consumer: Tenant): AgreementState =
    currentState match {
      case DRAFT                        =>
        if (!certifiedAttributesSatisfied(eService, consumer)) MISSING_CERTIFIED_ATTRIBUTES
        else if (declaredAttributesSatisfied(eService, consumer)) PENDING
        else DRAFT
      case PENDING                      =>
        if (!certifiedAttributesSatisfied(eService, consumer)) MISSING_CERTIFIED_ATTRIBUTES
        else if (!declaredAttributesSatisfied(eService, consumer)) DRAFT
        else if (!verifiedAttributesSatisfied(eService, consumer)) PENDING
        else ACTIVE
      case ACTIVE                       =>
        if (
          certifiedAttributesSatisfied(eService, consumer) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(eService, consumer)
        )
          ACTIVE
        else
          SUSPENDED
      case SUSPENDED                    =>
        if (
          certifiedAttributesSatisfied(eService, consumer) &&
          declaredAttributesSatisfied(eService, consumer) &&
          verifiedAttributesSatisfied(eService, consumer)
        )
          ACTIVE
        else
          SUSPENDED
      case INACTIVE                     => INACTIVE
      case MISSING_CERTIFIED_ATTRIBUTES =>
        if (certifiedAttributesSatisfied(eService, consumer)) DRAFT
        else MISSING_CERTIFIED_ATTRIBUTES
    }

  def certifiedAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    attributesSatisfied(eService.attributes.certified, consumer.attributes.flatMap(_.certified).map(_.id))

  def declaredAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    attributesSatisfied(eService.attributes.declared, consumer.attributes.flatMap(_.declared).map(_.id))

  def verifiedAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    attributesSatisfied(eService.attributes.verified, consumer.attributes.flatMap(_.verified).map(_.id))

  def attributesSatisfied(requested: Seq[Attribute], assigned: Seq[UUID]): Boolean =
    requested.forall {
      case Attribute(Some(single), _) => assigned.contains(single.id)
      case Attribute(_, Some(group))  => group.map(_.id).intersect(assigned).nonEmpty
      case _                          => true
    }
}
