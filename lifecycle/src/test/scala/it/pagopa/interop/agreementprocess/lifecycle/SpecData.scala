package it.pagopa.interop.agreementprocess.lifecycle

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import it.pagopa.interop.catalogmanagement.model.{
  CatalogItem,
  Rest,
  CatalogAttributes,
  SingleAttribute,
  CatalogAttributeValue,
  GroupAttribute
}
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentVerifiedAttribute,
  PersistentTenantVerifier,
  PersistentTenantRevoker,
  PersistentTenant,
  PersistentTenantKind,
  PersistentExternalId
}
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, Active, PersistentStamps}

object SpecData {

  final val timestamp: OffsetDateTime = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)

  def eService: CatalogItem = CatalogItem(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "EService1",
    description = "EService 1",
    technology = Rest,
    attributes = CatalogAttributes(Nil, Nil, Nil),
    descriptors = Nil,
    createdAt = timestamp
  )

  def tenant: PersistentTenant = PersistentTenant(
    id = UUID.randomUUID(),
    selfcareId = Some(UUID.randomUUID().toString),
    externalId = PersistentExternalId("origin", "value"),
    features = Nil,
    attributes = Nil,
    createdAt = OffsetDateTime.now(),
    updatedAt = None,
    mails = Nil,
    name = "test_name",
    kind = Some(PersistentTenantKind.PA)
  )

  def catalogSingleAttribute(id: UUID = UUID.randomUUID()): SingleAttribute =
    SingleAttribute(id = CatalogAttributeValue(id, explicitAttributeVerification = false))

  def catalogGroupAttributes(id1: UUID = UUID.randomUUID(), id2: UUID = UUID.randomUUID()): GroupAttribute =
    GroupAttribute(ids =
      Seq(
        CatalogAttributeValue(id1, explicitAttributeVerification = false),
        CatalogAttributeValue(id2, explicitAttributeVerification = false)
      )
    )

  def catalogCertifiedAttribute(id: UUID = UUID.randomUUID()): CatalogAttributes =
    CatalogAttributes(certified = Seq(catalogSingleAttribute(id)), declared = Nil, verified = Nil)

  def catalogDeclaredAttribute(id: UUID = UUID.randomUUID()): CatalogAttributes =
    CatalogAttributes(declared = Seq(catalogSingleAttribute(id)), certified = Nil, verified = Nil)

  def catalogVerifiedAttribute(id: UUID = UUID.randomUUID()): CatalogAttributes =
    CatalogAttributes(declared = Nil, certified = Nil, verified = Seq(catalogSingleAttribute(id)))

  def tenantCertifiedAttribute(id: UUID = UUID.randomUUID()): PersistentCertifiedAttribute =
    PersistentCertifiedAttribute(id = id, assignmentTimestamp = timestamp, revocationTimestamp = None)

  def tenantDeclaredAttribute(id: UUID = UUID.randomUUID()): PersistentDeclaredAttribute =
    PersistentDeclaredAttribute(id = id, assignmentTimestamp = timestamp, revocationTimestamp = None)

  def tenantVerifiedAttribute(
    id: UUID = UUID.randomUUID(),
    verifierId: UUID = UUID.randomUUID(),
    extensionDate: Option[OffsetDateTime] = Some(timestamp.plusYears(9))
  ): PersistentVerifiedAttribute =
    PersistentVerifiedAttribute(
      id = id,
      assignmentTimestamp = timestamp,
      verifiedBy = List(
        PersistentTenantVerifier(
          id = verifierId,
          verificationDate = timestamp,
          extensionDate = extensionDate,
          expirationDate = None
        )
      ),
      revokedBy = Nil
    )

  def tenantRevokedCertifiedAttribute(id: UUID = UUID.randomUUID()): PersistentCertifiedAttribute =
    PersistentCertifiedAttribute(id = id, assignmentTimestamp = timestamp, revocationTimestamp = Some(timestamp))

  def tenantRevokedDeclaredAttribute(id: UUID = UUID.randomUUID()): PersistentDeclaredAttribute =
    PersistentDeclaredAttribute(id = id, assignmentTimestamp = timestamp, revocationTimestamp = Some(timestamp))

  def tenantRevokedVerifiedAttribute(
    id: UUID = UUID.randomUUID(),
    revokerId: UUID = UUID.randomUUID()
  ): PersistentVerifiedAttribute =
    PersistentVerifiedAttribute(
      id = id,
      assignmentTimestamp = timestamp,
      verifiedBy = Nil,
      revokedBy = List(
        PersistentTenantRevoker(
          id = revokerId,
          verificationDate = timestamp,
          revocationDate = timestamp,
          expirationDate = None,
          extensionDate = None
        )
      )
    )

  def agreement: PersistentAgreement = PersistentAgreement(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    descriptorId = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    state = Active,
    certifiedAttributes = Nil,
    declaredAttributes = Nil,
    verifiedAttributes = Nil,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    suspendedByPlatform = None,
    consumerDocuments = Nil,
    createdAt = OffsetDateTime.now(),
    contract = None,
    stamps = PersistentStamps(),
    updatedAt = Some(timestamp),
    consumerNotes = None,
    rejectionReason = None,
    suspendedAt = None
  )

}
