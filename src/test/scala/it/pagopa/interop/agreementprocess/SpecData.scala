package it.pagopa.interop.agreementprocess

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.service.ClientAttribute
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeManagement}
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.AUTOMATIC
import it.pagopa.interop.catalogmanagement.client.model.EServiceTechnology.REST
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.selfcare.partymanagement.client.model.Institution
import it.pagopa.interop.selfcare.userregistry.client.model.CertifiableFieldResourceOfstringEnums.Certification
import it.pagopa.interop.selfcare.userregistry.client.model.{CertifiableFieldResourceOfstring, UserResource}
import it.pagopa.interop.tenantmanagement.client.model._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object SpecData {

  final val timestamp: OffsetDateTime   = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)
  final val emptyStamps: Stamps         = Stamps()
  val who: UUID                         = UUID.randomUUID()
  val when: OffsetDateTime              = OffsetDateTime.now()
  final val defaultStamp: Option[Stamp] = Stamp(who, when).some

  final val submissionStamps = emptyStamps.copy(submission = defaultStamp)
  final val rejectionStamps  = submissionStamps.copy(rejection = defaultStamp)
  final val activationStamps = submissionStamps.copy(activation = defaultStamp)
  final val suspensionStamps = activationStamps.copy(suspension = defaultStamp)
  final val archivingStamps  = suspensionStamps.copy(archiving = defaultStamp)

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
    state = EServiceDescriptorState.PUBLISHED,
    agreementApprovalPolicy = AUTOMATIC
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

  def tenantVerifiedAttribute(id: UUID = UUID.randomUUID(), verifierId: UUID = UUID.randomUUID()): TenantAttribute =
    TenantAttribute(verified =
      Some(
        VerifiedTenantAttribute(
          id = id,
          assignmentTimestamp = timestamp,
          verifiedBy = Seq(
            TenantVerifier(
              id = verifierId,
              verificationDate = timestamp,
              renewal = VerificationRenewal.AUTOMATIC_RENEWAL
            )
          ),
          revokedBy = Nil
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

  def matchingVerifiedAttributes(verifierId: UUID = UUID.randomUUID()): (Attributes, TenantAttribute) = {
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

  def draftAgreement: Agreement     = agreement.copy(state = AgreementState.DRAFT)
  def pendingAgreement: Agreement   = agreement.copy(state = AgreementState.PENDING, stamps = submissionStamps)
  def suspendedAgreement: Agreement =
    agreement.copy(state = AgreementState.SUSPENDED, stamps = suspensionStamps, suspendedByProducer = Some(true))
  def activeAgreement: Agreement    = agreement.copy(state = AgreementState.ACTIVE, stamps = activationStamps)
  def archivedAgreement: Agreement  = agreement.copy(state = AgreementState.ARCHIVED, stamps = archivingStamps)
  def missingCertifiedAttributesAgreement: Agreement =
    agreement.copy(state = AgreementState.MISSING_CERTIFIED_ATTRIBUTES)

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

  def clientAttribute(id: UUID): ClientAttribute = AttributeManagement.Attribute(
    id = id,
    code = "code".some,
    kind = AttributeManagement.AttributeKind.CERTIFIED,
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

  def institution(id: UUID): Institution = Institution(
    id = id,
    externalId = UUID.randomUUID().toString,
    originId = UUID.randomUUID().toString,
    description = "producer",
    digitalAddress = UUID.randomUUID().toString,
    address = UUID.randomUUID().toString,
    zipCode = UUID.randomUUID().toString,
    taxCode = UUID.randomUUID().toString,
    origin = UUID.randomUUID().toString,
    institutionType = UUID.randomUUID().toString,
    attributes = Seq.empty
  )
}
