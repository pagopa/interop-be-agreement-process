package it.pagopa.interop.agreementprocess.lifecycle

import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.catalogmanagement.client.model.EServiceTechnology.REST
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.tenantmanagement.client.model._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object SpecData {

  final val timestamp: OffsetDateTime = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)

  def eService: EService = EService(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "EService 1",
    technology = REST,
    descriptors = Nil
  )

  def descriptor: EServiceDescriptor = EServiceDescriptor(
    id = UUID.randomUUID(),
    version = "1",
    description = None,
    audience = Nil,
    voucherLifespan = 0,
    dailyCallsPerConsumer = 0,
    dailyCallsTotal = 0,
    interface = None,
    docs = Nil,
    state = EServiceDescriptorState.PUBLISHED,
    agreementApprovalPolicy = AgreementApprovalPolicy.AUTOMATIC,
    serverUrls = Nil,
    publishedAt = None,
    suspendedAt = None,
    deprecatedAt = None,
    archivedAt = None,
    attributes = Attributes(Nil, Nil, Nil)
  )

  def tenant: Tenant = Tenant(
    id = UUID.randomUUID(),
    selfcareId = Some(UUID.randomUUID().toString),
    externalId = ExternalId("origin", "value"),
    features = Nil,
    attributes = Nil,
    createdAt = OffsetDateTime.now(),
    updatedAt = None,
    mails = Nil,
    name = "test_name"
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

  def tenantVerifiedAttribute(
    id: UUID = UUID.randomUUID(),
    verifierId: UUID = UUID.randomUUID(),
    extensionDate: Option[OffsetDateTime] = Some(timestamp.plusYears(9))
  ): TenantAttribute =
    TenantAttribute(verified =
      Some(
        VerifiedTenantAttribute(
          id = id,
          assignmentTimestamp = timestamp,
          verifiedBy =
            Seq(TenantVerifier(id = verifierId, verificationDate = timestamp, extensionDate = extensionDate)),
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

  def tenantRevokedVerifiedAttribute(
    id: UUID = UUID.randomUUID(),
    revokerId: UUID = UUID.randomUUID()
  ): TenantAttribute =
    TenantAttribute(verified =
      Some(
        VerifiedTenantAttribute(
          id = id,
          assignmentTimestamp = timestamp,
          verifiedBy = Nil,
          revokedBy = Seq(TenantRevoker(id = revokerId, verificationDate = timestamp, revocationDate = timestamp))
        )
      )
    )

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
    createdAt = OffsetDateTime.now(),
    contract = None,
    stamps = Stamps()
  )

}
