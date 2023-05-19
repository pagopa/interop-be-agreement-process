package it.pagopa.interop.agreementprocess.lifecycle

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.catalogmanagement.client.model.{Attribute, Attributes, EService}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantmanagement.client.model.VerificationRenewal.{AUTOMATIC_RENEWAL, REVOKE_ON_EXPIRATION}
import it.pagopa.interop.tenantmanagement.client.model.{
  CertifiedTenantAttribute,
  DeclaredTenantAttribute,
  Tenant,
  TenantVerifier,
  VerifiedTenantAttribute
}

import java.util.UUID

object AttributesRules {

  def certifiedAttributesSatisfied(
    eServiceAttributes: Attributes,
    consumerAttributes: Seq[CertifiedTenantAttribute]
  ): Boolean = attributesSatisfied(
    eServiceAttributes.certified,
    consumerAttributes.filter(_.revocationTimestamp.isEmpty).map(_.id)
  )

  def certifiedAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    certifiedAttributesSatisfied(eService.attributes, consumer.attributes.mapFilter(_.certified))

  def declaredAttributesSatisfied(
    eServiceAttributes: Attributes,
    consumerAttributes: Seq[DeclaredTenantAttribute]
  ): Boolean =
    attributesSatisfied(eServiceAttributes.declared, consumerAttributes.filter(_.revocationTimestamp.isEmpty).map(_.id))

  def declaredAttributesSatisfied(eService: EService, consumer: Tenant): Boolean =
    declaredAttributesSatisfied(eService.attributes, consumer.attributes.mapFilter(_.declared))

  def verifiedAttributesSatisfied(
    producerId: UUID,
    eServiceAttributes: Attributes,
    consumerAttributes: Seq[VerifiedTenantAttribute]
  ): Boolean = attributesSatisfied(
    eServiceAttributes.verified,
    consumerAttributes
      .filter(_.verifiedBy.exists(v => v.id == producerId && isNotExpired(v)))
      .map(_.id)
  )

  private def isNotExpired(verifier: TenantVerifier): Boolean = {
    (verifier.renewal == REVOKE_ON_EXPIRATION && verifier.extensionDate
      .exists(ed => ed.isAfter(OffsetDateTimeSupplier.get()))) || verifier.renewal == AUTOMATIC_RENEWAL
  }

  def verifiedAttributesSatisfied(agreement: Agreement, eService: EService, consumer: Tenant): Boolean =
    verifiedAttributesSatisfied(agreement.producerId, eService.attributes, consumer.attributes.mapFilter(_.verified))

  private def attributesSatisfied(requested: Seq[Attribute], assigned: Seq[UUID]): Boolean =
    requested.forall {
      case Attribute(Some(single), _) => assigned.contains(single.id)
      case Attribute(_, Some(group))  => group.map(_.id).intersect(assigned).nonEmpty
      case _                          => true
    }
}
