package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.model.UpdateAgreementSeed
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState
import it.pagopa.interop.selfcare.partymanagement.client.model.Institution
import it.pagopa.interop.selfcare.userregistry.client.model.CertifiableFieldResourceOfstringEnums.Certification
import it.pagopa.interop.selfcare.userregistry.client.model.{CertifiableFieldResourceOfstring, UserResource}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementActivationSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Activation" should {
    "succeed on Pending agreement when Consumer has required attributes" in {
      import AgreementManagement.{CertifiedAttribute, DeclaredAttribute, VerifiedAttribute}

      val producerId = requesterOrgId
      val certAttr1  = UUID.randomUUID()
      val certAttr2  = UUID.randomUUID()
      val declAttr1  = UUID.randomUUID()
      val declAttr2  = UUID.randomUUID()
      val verAttr1   = UUID.randomUUID()
      val verAttr2   = UUID.randomUUID()

      val eServiceCertAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(SpecData.catalogSingleAttribute(certAttr1), SpecData.catalogGroupAttributes(id1 = certAttr2))
        )
      val tenantCertAttr   =
        Seq(SpecData.tenantCertifiedAttribute(certAttr1), SpecData.tenantCertifiedAttribute(certAttr2))

      val eServiceDeclAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(SpecData.catalogSingleAttribute(declAttr1), SpecData.catalogGroupAttributes(id1 = declAttr2))
        )
      val tenantDeclAttr   =
        Seq(SpecData.tenantDeclaredAttribute(declAttr1), SpecData.tenantDeclaredAttribute(declAttr2))

      val eServiceVerAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(SpecData.catalogSingleAttribute(verAttr1), SpecData.catalogGroupAttributes(id1 = verAttr2))
        )
      val tenantVerAttr   =
        Seq(
          SpecData.tenantVerifiedAttribute(verAttr1, producerId),
          SpecData.tenantVerifiedAttribute(verAttr2, producerId)
        )

      val eServiceAttr =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr   = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr).flatten

      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr, producerId = producerId)
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr)
      val agreement  =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val consumerInstitution = Institution(
        id = UUID.randomUUID(),
        externalId = UUID.randomUUID().toString(),
        originId = consumer.externalId.value,
        description = "consumer",
        digitalAddress = UUID.randomUUID().toString(),
        address = UUID.randomUUID().toString(),
        zipCode = UUID.randomUUID().toString(),
        taxCode = UUID.randomUUID().toString(),
        origin = consumer.externalId.origin,
        institutionType = UUID.randomUUID().toString(),
        attributes = Seq.empty
      )

      val consumerAdmin = UserResource(
        familyName = Some(CertifiableFieldResourceOfstring(Certification.SPID, "admin")),
        fiscalCode = Some(UUID.randomUUID().toString()),
        id = SpecData.who,
        name = Some(CertifiableFieldResourceOfstring(Certification.SPID, "consumer"))
      )

      val producerAdmin = UserResource(
        familyName = Some(CertifiableFieldResourceOfstring(Certification.SPID, "admin")),
        fiscalCode = Some(UUID.randomUUID().toString()),
        id = SpecData.who,
        name = Some(CertifiableFieldResourceOfstring(Certification.SPID, "producer"))
      )

      val producerInstitution = Institution(
        id = UUID.randomUUID(),
        externalId = UUID.randomUUID().toString(),
        originId = UUID.randomUUID().toString(),
        description = "producer",
        digitalAddress = UUID.randomUUID().toString(),
        address = UUID.randomUUID().toString(),
        zipCode = UUID.randomUUID().toString(),
        taxCode = UUID.randomUUID().toString(),
        origin = UUID.randomUUID().toString(),
        institutionType = UUID.randomUUID().toString(),
        attributes = Seq.empty
      )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.ACTIVE,
        certifiedAttributes = Seq(CertifiedAttribute(certAttr1), CertifiedAttribute(certAttr2)),
        declaredAttributes = Seq(DeclaredAttribute(declAttr1), DeclaredAttribute(declAttr2)),
        verifiedAttributes = Seq(VerifiedAttribute(verAttr1), VerifiedAttribute(verAttr2)),
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(false),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
      mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
      mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
      mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
      mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
      mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
      mockPDFCreatorCreate
      mockFileManagerWrite
      mockAgreementContract
      mockPartyManagementRetrieve(producerInstitution)
      mockPartyManagementRetrieve(consumerInstitution)
      mockUserRegistryRetrieve(consumerAdmin)
      mockUserRegistryRetrieve(producerAdmin)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed on Suspended agreement (by Consumer) when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.suspendedAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          suspendedByConsumer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.ACTIVE,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = Some(false),
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed on Suspended agreement (by Producer) when requested by Producer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.suspendedAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByProducer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.ACTIVE,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(false),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Consumer) when requested by Producer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.suspendedAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByConsumer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = Some(true),
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(false),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Producer) when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.suspendedAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByProducer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = Some(false),
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(false),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Platform) when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = SpecData.catalogCertifiedAttribute())
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.suspendedAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByPlatform = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = Some(false),
        suspendedByProducer = None,
        suspendedByPlatform = Some(true),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Platform) when requested by Producer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(
          descriptors = Seq(descriptor),
          attributes = SpecData.catalogCertifiedAttribute(),
          producerId = requesterOrgId
        )
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.suspendedAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByPlatform = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(true),
        stamps = SpecData.activationStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail and invalidate agreement if tenant lost a certified attribute" in {
      val producerId: UUID                   = requesterOrgId
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantVerAttr, tenantDeclAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr, producerId = producerId)
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr)
      val agreement  =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(true),
        stamps = SpecData.submissionStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail and move to Draft on missing declared attributes" in {
      val producerId                         = requesterOrgId
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantVerAttr, tenantCertAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr, producerId = requesterOrgId)
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr)
      val agreement  =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.DRAFT,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        // TODO The action is done by the platform, but it's not suspended. What value should it have?
        suspendedByPlatform = Some(false),
        stamps = SpecData.submissionStamps
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail on Pending agreement when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(requesterOrgId)
      val agreement  =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      mockAgreementRetrieve(agreement)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()

      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.activateAgreement(agreementId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if Agreement is not in expected state" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if other Agreements exist in conflicting state" in {
      val agreement = SpecData.pendingAgreement.copy(producerId = requesterOrgId)

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Seq(SpecData.agreement))

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if Descriptor is not in expected state" in {
      val descriptor = SpecData.archivedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Consumer or the Producer" in {
      val eService  = SpecData.eService.copy(producerId = UUID.randomUUID())
      val agreement = SpecData.suspendedAgreement.copy(eserviceId = eService.id, consumerId = UUID.randomUUID())

      mockAgreementRetrieve(agreement)

      Get() ~> service.activateAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

  }

}
