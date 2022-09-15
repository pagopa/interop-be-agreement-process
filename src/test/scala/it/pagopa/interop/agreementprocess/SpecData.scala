package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model.{
  Agreement,
  AgreementState,
  CertifiedAttribute,
  DeclaredAttribute,
  VerifiedAttribute
}
import it.pagopa.interop.catalogmanagement.client.model.{
  Attribute,
  AttributeValue,
  Attributes,
  EService,
  EServiceDescriptor,
  EServiceDescriptorState
}
import it.pagopa.interop.catalogmanagement.client.model.EServiceTechnology.REST
import it.pagopa.interop.tenantmanagement.client.model.{
  CertifiedTenantAttribute,
  DeclaredTenantAttribute,
  ExternalId,
  Tenant,
  TenantAttribute,
  TenantRevoker,
  TenantVerifier,
  VerificationRenewal,
  VerifiedTenantAttribute
}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object SpecData {

  final val timestamp: OffsetDateTime = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)

  def descriptor: EServiceDescriptor = EServiceDescriptor(
    id = UUID.randomUUID(),
    version = "1",
    description = None,
    audience = Nil,
    voucherLifespan = 10,
    dailyCallsPerConsumer = 100,
    dailyCallsTotal = 1000,
    interface = None,
    docs = Nil,
    state = EServiceDescriptorState.PUBLISHED
  )

  def publishedDescriptor: EServiceDescriptor  = descriptor.copy(state = EServiceDescriptorState.PUBLISHED)
  def deprecatedDescriptor: EServiceDescriptor = descriptor.copy(state = EServiceDescriptorState.DEPRECATED)
  def archivedDescriptor: EServiceDescriptor   = descriptor.copy(state = EServiceDescriptorState.ARCHIVED)

  def eService: EService = EService(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "EService 1",
    technology = REST,
    attributes = Attributes(Nil, Nil, Nil),
    descriptors = Nil
  )

  def tenant: Tenant = Tenant(
    id = UUID.randomUUID(),
    selfcareId = Some(UUID.randomUUID().toString),
    externalId = ExternalId("origin", "value"),
    features = Nil,
    attributes = Nil,
    createdAt = OffsetDateTime.now(),
    updatedAt = None
  )

  def catalogSingleAttribute(id: UUID = UUID.randomUUID()): Attribute =
    Attribute(single = Some(AttributeValue(id, explicitAttributeVerification = false)))

  def catalogGroupAttributes(id1: UUID = UUID.randomUUID(), id2: UUID = UUID.randomUUID()): Attribute =
    Attribute(group =
      Some(
        Seq(
          AttributeValue(id1, explicitAttributeVerification = false),
          AttributeValue(id2, explicitAttributeVerification = false)
        )
      )
    )

  def catalogCertifiedAttribute(id: UUID = UUID.randomUUID()): Attributes =
    Attributes(certified = Seq(catalogSingleAttribute(id)), declared = Nil, verified = Nil)

  def catalogDeclaredAttribute(id: UUID = UUID.randomUUID()): Attributes =
    Attributes(declared = Seq(catalogSingleAttribute(id)), certified = Nil, verified = Nil)

  def catalogVerifiedAttribute(id: UUID = UUID.randomUUID()): Attributes =
    Attributes(declared = Nil, certified = Nil, verified = Seq(catalogSingleAttribute(id)))

  def tenantCertifiedAttribute(id: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(certified = Some(CertifiedTenantAttribute(id = id, assignmentTimestamp = timestamp)))

  def tenantDeclaredAttribute(id: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(declared = Some(DeclaredTenantAttribute(id = id, assignmentTimestamp = timestamp)))

  def tenantVerifiedAttribute(id: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(verified =
      Some(
        VerifiedTenantAttribute(
          id = id,
          assignmentTimestamp = timestamp,
          renewal = VerificationRenewal.AUTOMATIC_RENEWAL,
          verifiedBy = Seq(TenantVerifier(id = UUID.randomUUID(), verificationDate = timestamp)),
          revokedBy = Nil
        )
      )
    )

  def tenantRevokedCertifiedAttribute(id: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(certified =
      Some(CertifiedTenantAttribute(id = id, assignmentTimestamp = timestamp, revocationTimestamp = Some(timestamp)))
    )

  def tenantRevokedDeclaredAttribute(id: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(declared =
      Some(DeclaredTenantAttribute(id = id, assignmentTimestamp = timestamp, revocationTimestamp = Some(timestamp)))
    )

  def tenantRevokedVerifiedAttribute(id: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(verified =
      Some(
        VerifiedTenantAttribute(
          id = id,
          assignmentTimestamp = timestamp,
          renewal = VerificationRenewal.AUTOMATIC_RENEWAL,
          verifiedBy = Nil,
          revokedBy =
            Seq(TenantRevoker(id = UUID.randomUUID(), verificationDate = timestamp, revocationDate = timestamp))
        )
      )
    )

  def matchingCertifiedAttributes: (Attributes, TenantAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = catalogCertifiedAttribute(attributeId)
    val tenantAttribute   = tenantCertifiedAttribute(attributeId)

    (eServiceAttribute, tenantAttribute)
  }

  def matchingDeclaredAttributes: (Attributes, TenantAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = catalogDeclaredAttribute(attributeId)
    val tenantAttribute   = tenantDeclaredAttribute(attributeId)

    (eServiceAttribute, tenantAttribute)
  }

  def matchingVerifiedAttributes: (Attributes, TenantAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = catalogCertifiedAttribute(attributeId)
    val tenantAttribute   = tenantVerifiedAttribute(attributeId)

    (eServiceAttribute, tenantAttribute)
  }

  def agreement: Agreement = Agreement(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    descriptorId = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    state = AgreementState.ACTIVE,
    certifiedAttributes = Nil,
    declaredAttributes = Nil,
    verifiedAttributes = Nil,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    suspendedByPlatform = None,
    consumerDocuments = Nil,
    createdAt = OffsetDateTime.now()
  )

  def draftAgreement: Agreement             = agreement.copy(state = AgreementState.DRAFT)
  def pendingAgreement: Agreement           = agreement.copy(state = AgreementState.PENDING)
  def suspendedAgreement: Agreement         = agreement.copy(state = AgreementState.SUSPENDED)
  def activeAgreement: Agreement            = agreement.copy(state = AgreementState.ACTIVE)
  def archivedAgreement: Agreement          = agreement.copy(state = AgreementState.ARCHIVED)
  def missingCertifiedAttributes: Agreement = agreement.copy(state = AgreementState.MISSING_CERTIFIED_ATTRIBUTES)

  def activeAgreementWithAttributes: Agreement = activeAgreement.copy(
    certifiedAttributes = Seq(CertifiedAttribute(UUID.randomUUID()), CertifiedAttribute(UUID.randomUUID())),
    declaredAttributes = Seq(DeclaredAttribute(UUID.randomUUID()), DeclaredAttribute(UUID.randomUUID())),
    verifiedAttributes = Seq(VerifiedAttribute(UUID.randomUUID()), VerifiedAttribute(UUID.randomUUID()))
  )

  def suspendedAgreementWithAttributes: Agreement = suspendedAgreement.copy(
    certifiedAttributes = Seq(CertifiedAttribute(UUID.randomUUID()), CertifiedAttribute(UUID.randomUUID())),
    declaredAttributes = Seq(DeclaredAttribute(UUID.randomUUID()), DeclaredAttribute(UUID.randomUUID())),
    verifiedAttributes = Seq(VerifiedAttribute(UUID.randomUUID()), VerifiedAttribute(UUID.randomUUID()))
  )

}
