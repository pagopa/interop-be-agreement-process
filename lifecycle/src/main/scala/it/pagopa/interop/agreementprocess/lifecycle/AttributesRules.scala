package it.pagopa.interop.agreementprocess.lifecycle

import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.catalogmanagement.model.{CatalogDescriptor, CatalogAttribute, CatalogAttributes}
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentVerifiedAttribute,
  PersistentTenantAttribute,
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

  def certifiedAttributesSatisfied(
    descriptor: CatalogDescriptor,
    consumerAttributes: List[PersistentTenantAttribute]
  ): Boolean =
    certifiedAttributesSatisfied(
      descriptor.attributes,
      consumerAttributes.collect { case a: PersistentCertifiedAttribute => a }
    )

  def declaredAttributesSatisfied(
    eServiceAttributes: CatalogAttributes,
    consumerAttributes: Seq[PersistentDeclaredAttribute]
  ): Boolean =
    attributesSatisfied(eServiceAttributes.declared, consumerAttributes.filter(_.revocationTimestamp.isEmpty).map(_.id))

  def declaredAttributesSatisfied(
    descriptor: CatalogDescriptor,
    consumerAttributes: List[PersistentTenantAttribute]
  ): Boolean =
    declaredAttributesSatisfied(
      descriptor.attributes,
      consumerAttributes.collect { case a: PersistentDeclaredAttribute => a }
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

  private def isNotExpired(verifier: PersistentTenantVerifier): Boolean =
    verifier.extensionDate.forall(ed => ed.isAfter(OffsetDateTimeSupplier.get()))

  def verifiedAttributesSatisfied(
    agreement: PersistentAgreement,
    descriptor: CatalogDescriptor,
    consumerAttributes: List[PersistentTenantAttribute]
  ): Boolean =
    verifiedAttributesSatisfied(
      agreement.producerId,
      descriptor.attributes,
      consumerAttributes.collect { case a: PersistentVerifiedAttribute => a }
    )

  private def attributesSatisfied(requested: Seq[Seq[CatalogAttribute]], assigned: Seq[UUID]): Boolean =
    requested.forall(_.map(_.id).intersect(assigned).nonEmpty)
}
