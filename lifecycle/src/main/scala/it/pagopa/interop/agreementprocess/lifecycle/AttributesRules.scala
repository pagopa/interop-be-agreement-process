package it.pagopa.interop.agreementprocess.lifecycle

import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.catalogmanagement.model.{
  CatalogDescriptor,
  CatalogAttribute,
  SingleAttribute,
  GroupAttribute,
  CatalogAttributes
}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentVerifiedAttribute,
  PersistentTenant,
  PersistentTenantVerifier
}

import java.util.UUID

object AttributesRules {

  def certifiedAttributesSatisfied(
    eServiceAttributes: CatalogAttributes,
    consumerAttributes: Seq[PersistentCertifiedAttribute]
  ): Boolean = attributesSatisfied(
    eServiceAttributes.certified,
    consumerAttributes.filter(_.revocationTimestamp.isEmpty).map(_.id)
  )

  def certifiedAttributesSatisfied(descriptor: CatalogDescriptor, consumer: PersistentTenant): Boolean =
    certifiedAttributesSatisfied(
      descriptor.attributes,
      consumer.attributes.collect { case a: PersistentCertifiedAttribute => a }
    )

  def declaredAttributesSatisfied(
    eServiceAttributes: CatalogAttributes,
    consumerAttributes: Seq[PersistentDeclaredAttribute]
  ): Boolean =
    attributesSatisfied(eServiceAttributes.declared, consumerAttributes.filter(_.revocationTimestamp.isEmpty).map(_.id))

  def declaredAttributesSatisfied(descriptor: CatalogDescriptor, consumer: PersistentTenant): Boolean =
    declaredAttributesSatisfied(
      descriptor.attributes,
      consumer.attributes.collect { case a: PersistentDeclaredAttribute => a }
    )

  def verifiedAttributesSatisfied(
    producerId: UUID,
    eServiceAttributes: CatalogAttributes,
    consumerAttributes: Seq[PersistentVerifiedAttribute]
  ): Boolean = attributesSatisfied(
    eServiceAttributes.verified,
    consumerAttributes
      .filter(_.verifiedBy.exists(v => v.id == producerId && isNotExpired(v)))
      .map(_.id)
  )

  private def isNotExpired(verifier: PersistentTenantVerifier): Boolean = {
    verifier.extensionDate
      .exists(ed => ed.isAfter(OffsetDateTimeSupplier.get()))
  }

  def verifiedAttributesSatisfied(
    agreement: PersistentAgreement,
    descriptor: CatalogDescriptor,
    consumer: PersistentTenant
  ): Boolean =
    verifiedAttributesSatisfied(
      agreement.producerId,
      descriptor.attributes,
      consumer.attributes.collect { case a: PersistentVerifiedAttribute => a }
    )

  private def attributesSatisfied(requested: Seq[CatalogAttribute], assigned: Seq[UUID]): Boolean = {
    requested.forall {
      case SingleAttribute(value) => assigned.contains(value.id)
      case GroupAttribute(values) => values.map(_.id).intersect(assigned).nonEmpty
      case _                      => true
    }
  }
}
