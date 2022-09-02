package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
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
  TenantAttribute
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

  def publishedDescriptor: EServiceDescriptor = descriptor.copy(state = EServiceDescriptorState.PUBLISHED)
  def archivedDescriptor: EServiceDescriptor  = descriptor.copy(state = EServiceDescriptorState.ARCHIVED)

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

  def catalogAttribute(id: UUID = UUID.randomUUID()): Attribute =
    Attribute(single = Some(AttributeValue(id, false)))

  def catalogCertifiedAttribute: Attributes =
    Attributes(certified = Seq(catalogAttribute()), declared = Nil, verified = Nil)

  def catalogDeclaredAttribute: Attributes =
    Attributes(declared = Seq(catalogAttribute()), certified = Nil, verified = Nil)

  def tenantCertifiedAttribute: TenantAttribute =
    TenantAttribute(certified = Some(CertifiedTenantAttribute(id = UUID.randomUUID(), assignmentTimestamp = timestamp)))

  def tenantDeclaredAttribute: TenantAttribute =
    TenantAttribute(declared = Some(DeclaredTenantAttribute(id = UUID.randomUUID(), assignmentTimestamp = timestamp)))

  def matchingCertifiedAttributes: (Attributes, TenantAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = Attributes(certified = Seq(catalogAttribute(attributeId)), declared = Nil, verified = Nil)
    val tenantAttribute   =
      TenantAttribute(certified = Some(CertifiedTenantAttribute(id = attributeId, assignmentTimestamp = timestamp)))

    (eServiceAttribute, tenantAttribute)
  }

  def matchingDeclaredAttributes: (Attributes, TenantAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = Attributes(certified = Nil, declared = Seq(catalogAttribute(attributeId)), verified = Nil)
    val tenantAttribute   =
      TenantAttribute(declared = Some(DeclaredTenantAttribute(id = attributeId, assignmentTimestamp = timestamp)))

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
    consumerDocuments = Nil,
    createdAt = OffsetDateTime.now()
  )

  def draftAgreement: Agreement   = agreement.copy(state = AgreementState.DRAFT)
  def pendingAgreement: Agreement = agreement.copy(state = AgreementState.PENDING)
}
