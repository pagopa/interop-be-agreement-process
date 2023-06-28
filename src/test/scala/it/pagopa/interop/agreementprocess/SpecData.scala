package it.pagopa.interop.agreementprocess

import cats.syntax.all._
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.selfcare.userregistry.client.model.CertifiableFieldResourceOfstringEnums.Certification
import it.pagopa.interop.selfcare.userregistry.client.model.{CertifiableFieldResourceOfstring, UserResource}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import it.pagopa.interop.catalogmanagement.model.{
  CatalogDescriptor,
  Published => CatalogPublished,
  Deprecated => CatalogDeprecated,
  Archived => CatalogArchived,
  Draft => CatalogDraft,
  Automatic,
  CatalogItem,
  Rest,
  CatalogAttributes,
  GroupAttribute,
  SingleAttribute,
  CatalogAttributeValue
}
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentTenant,
  PersistentExternalId,
  PersistentTenantKind,
  PersistentDeclaredAttribute,
  PersistentCertifiedAttribute,
  PersistentVerifiedAttribute,
  PersistentTenantVerifier
}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.{Certified, PersistentAttribute}
import it.pagopa.interop.agreementmanagement.model.agreement.{
  PersistentAgreement,
  PersistentStamps,
  PersistentAgreementDocument,
  Rejected,
  Active,
  Draft
}
import it.pagopa.interop.agreementmanagement.model.agreement.Pending
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentStamp
import it.pagopa.interop.agreementmanagement.model.agreement.Suspended

object SpecData {

  final val timestamp: OffsetDateTime   = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)
  final val emptyStamps: Stamps         = Stamps()
  val who: UUID                         = UUID.randomUUID()
  val when: OffsetDateTime              = OffsetDateTime.now()
  final val defaultStamp: Option[Stamp] = Stamp(who, when).some
  final val submissionStamps            = emptyStamps.copy(submission = defaultStamp)
  final val rejectionStamps             = submissionStamps.copy(rejection = defaultStamp)
  final val activationStamps            = submissionStamps.copy(activation = defaultStamp)
  final val suspensionByConsumerStamps  = activationStamps.copy(suspensionByConsumer = defaultStamp)
  final val suspensionByProducerStamps  = activationStamps.copy(suspensionByProducer = defaultStamp)
  final val suspensionByBothStamps      =
    activationStamps.copy(suspensionByConsumer = defaultStamp, suspensionByProducer = defaultStamp)
  final val archivingStamps             = activationStamps.copy(archiving = defaultStamp)

  def descriptor: CatalogDescriptor = CatalogDescriptor(
    id = UUID.randomUUID(),
    version = "1",
    description = None,
    audience = Nil,
    voucherLifespan = 10,
    dailyCallsPerConsumer = 100,
    dailyCallsTotal = 1000,
    interface = None,
    docs = Nil,
    state = CatalogPublished,
    agreementApprovalPolicy = Automatic.some,
    serverUrls = Nil,
    createdAt = timestamp,
    publishedAt = timestamp.some,
    suspendedAt = None,
    deprecatedAt = None,
    archivedAt = None
  )

  def publishedDescriptor: CatalogDescriptor  = descriptor.copy(state = CatalogPublished)
  def deprecatedDescriptor: CatalogDescriptor = descriptor.copy(state = CatalogDeprecated)
  def archivedDescriptor: CatalogDescriptor   = descriptor.copy(state = CatalogArchived)
  def draftDescriptor: CatalogDescriptor      = descriptor.copy(state = CatalogDraft)

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
    kind = PersistentTenantKind.PA.some
  )

  def catalogSingleAttribute(id: UUID = UUID.randomUUID()): SingleAttribute =
    SingleAttribute(CatalogAttributeValue(id, explicitAttributeVerification = false))

  def catalogGroupAttributes(id1: UUID = UUID.randomUUID(), id2: UUID = UUID.randomUUID()): GroupAttribute =
    GroupAttribute(
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
    verifierId: UUID = UUID.randomUUID()
  ): PersistentVerifiedAttribute =
    PersistentVerifiedAttribute(
      id = id,
      assignmentTimestamp = timestamp,
      verifiedBy = List(
        PersistentTenantVerifier(
          id = verifierId,
          verificationDate = timestamp,
          extensionDate = timestamp.plusYears(9).some,
          expirationDate = None
        )
      ),
      revokedBy = Nil
    )

  def matchingCertifiedAttributes: (CatalogAttributes, PersistentCertifiedAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = catalogCertifiedAttribute(attributeId)
    val tenantAttribute   = tenantCertifiedAttribute(attributeId)

    (eServiceAttribute, tenantAttribute)
  }

  def matchingDeclaredAttributes: (CatalogAttributes, PersistentDeclaredAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = catalogDeclaredAttribute(attributeId)
    val tenantAttribute   = tenantDeclaredAttribute(attributeId)

    (eServiceAttribute, tenantAttribute)
  }

  def matchingVerifiedAttributes(
    verifierId: UUID = UUID.randomUUID()
  ): (CatalogAttributes, PersistentVerifiedAttribute) = {
    val attributeId       = UUID.randomUUID()
    val eServiceAttribute = catalogVerifiedAttribute(attributeId)
    val tenantAttribute   = tenantVerifiedAttribute(attributeId, verifierId)

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
    createdAt = OffsetDateTime.now(),
    contract = None,
    stamps = Stamps()
  )

  def rejectedAgreement: Agreement =
    agreement.copy(
      state = AgreementState.REJECTED,
      stamps = rejectionStamps,
      rejectionReason = Some("Document not valid")
    )

  def draftAgreement: Agreement = agreement.copy(state = AgreementState.DRAFT)

  def suspendedAgreement: Agreement = agreement.copy(state = AgreementState.SUSPENDED)

  def pendingAgreement: Agreement = agreement.copy(state = AgreementState.PENDING, stamps = submissionStamps)
  def suspendedByConsumerAgreement: Agreement =
    agreement.copy(
      state = AgreementState.SUSPENDED,
      stamps = suspensionByConsumerStamps,
      suspendedByConsumer = Some(true)
    )
  def suspendedByProducerAgreement: Agreement =
    agreement.copy(
      state = AgreementState.SUSPENDED,
      stamps = suspensionByProducerStamps,
      suspendedByProducer = Some(true)
    )
  def suspendedByPlatformAgreement: Agreement =
    agreement.copy(state = AgreementState.SUSPENDED, stamps = activationStamps, suspendedByPlatform = Some(true))
  def activeAgreement: Agreement              = agreement.copy(state = AgreementState.ACTIVE, stamps = activationStamps)
  def archivedAgreement: Agreement = agreement.copy(state = AgreementState.ARCHIVED, stamps = archivingStamps)
  def missingCertifiedAttributesAgreement: Agreement =
    agreement.copy(state = AgreementState.MISSING_CERTIFIED_ATTRIBUTES)

  def activeAgreementWithAttributes: Agreement = activeAgreement.copy(
    certifiedAttributes = Seq(CertifiedAttribute(UUID.randomUUID()), CertifiedAttribute(UUID.randomUUID())),
    declaredAttributes = Seq(DeclaredAttribute(UUID.randomUUID()), DeclaredAttribute(UUID.randomUUID())),
    verifiedAttributes = Seq(VerifiedAttribute(UUID.randomUUID()), VerifiedAttribute(UUID.randomUUID()))
  )

  def suspendedByConsumerAgreementWithAttributes: Agreement = suspendedByConsumerAgreement.copy(
    certifiedAttributes = Seq(CertifiedAttribute(UUID.randomUUID()), CertifiedAttribute(UUID.randomUUID())),
    declaredAttributes = Seq(DeclaredAttribute(UUID.randomUUID()), DeclaredAttribute(UUID.randomUUID())),
    verifiedAttributes = Seq(VerifiedAttribute(UUID.randomUUID()), VerifiedAttribute(UUID.randomUUID()))
  )

  def suspendedByProducerAgreementWithAttributes: Agreement = suspendedByProducerAgreement.copy(
    certifiedAttributes = Seq(CertifiedAttribute(UUID.randomUUID()), CertifiedAttribute(UUID.randomUUID())),
    declaredAttributes = Seq(DeclaredAttribute(UUID.randomUUID()), DeclaredAttribute(UUID.randomUUID())),
    verifiedAttributes = Seq(VerifiedAttribute(UUID.randomUUID()), VerifiedAttribute(UUID.randomUUID()))
  )

  def suspendedByPlatformAgreementWithAttributes: Agreement = suspendedByPlatformAgreement.copy(
    certifiedAttributes = Seq(CertifiedAttribute(UUID.randomUUID()), CertifiedAttribute(UUID.randomUUID())),
    declaredAttributes = Seq(DeclaredAttribute(UUID.randomUUID()), DeclaredAttribute(UUID.randomUUID())),
    verifiedAttributes = Seq(VerifiedAttribute(UUID.randomUUID()), VerifiedAttribute(UUID.randomUUID()))
  )

  def clientAttribute(id: UUID): PersistentAttribute = PersistentAttribute(
    id = id,
    code = "code".some,
    kind = Certified,
    description = "description",
    origin = "origin".some,
    name = "attr",
    creationTime = OffsetDateTime.now()
  )

  def document(id: UUID = UUID.randomUUID()): Document = Document(
    id = id,
    name = s"name_$id",
    prettyName = s"prettyName_$id",
    contentType = "application/json",
    path = s"path_$id",
    createdAt = timestamp
  )

  def userResource(name: String, familyName: String, fiscalCode: String): UserResource = UserResource(
    familyName = Some(CertifiableFieldResourceOfstring(Certification.SPID, familyName)),
    fiscalCode = Some(fiscalCode),
    id = SpecData.who,
    name = Some(CertifiableFieldResourceOfstring(Certification.SPID, name))
  )
}
