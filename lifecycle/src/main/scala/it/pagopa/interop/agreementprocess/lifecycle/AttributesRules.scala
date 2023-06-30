package it.pagopa.interop.agreementprocess.lifecycle

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.catalogmanagement.client.model.{Attribute, Attributes, EServiceDescriptor}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
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

  def certifiedAttributesSatisfied(descriptor: EServiceDescriptor, consumer: Tenant): Boolean =
    certifiedAttributesSatisfied(descriptor.attributes, consumer.attributes.mapFilter(_.certified))

  def declaredAttributesSatisfied(
    eServiceAttributes: Attributes,
    consumerAttributes: Seq[DeclaredTenantAttribute]
  ): Boolean =
    attributesSatisfied(eServiceAttributes.declared, consumerAttributes.filter(_.revocationTimestamp.isEmpty).map(_.id))

  def declaredAttributesSatisfied(descriptor: EServiceDescriptor, consumer: Tenant): Boolean =
    declaredAttributesSatisfied(descriptor.attributes, consumer.attributes.mapFilter(_.declared))

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

  private def isNotExpired(verifier: TenantVerifier): Boolean =
    verifier.extensionDate.forall(ed => ed.isAfter(OffsetDateTimeSupplier.get()))

  def verifiedAttributesSatisfied(agreement: Agreement, descriptor: EServiceDescriptor, consumer: Tenant): Boolean =
    verifiedAttributesSatisfied(agreement.producerId, descriptor.attributes, consumer.attributes.mapFilter(_.verified))

  private def attributesSatisfied(requested: Seq[Attribute], assigned: Seq[UUID]): Boolean =
    requested.forall {
      case Attribute(Some(single), _) => assigned.contains(single.id)
      case Attribute(_, Some(group))  => group.map(_.id).intersect(assigned).nonEmpty
      case _                          => true
    }
}
