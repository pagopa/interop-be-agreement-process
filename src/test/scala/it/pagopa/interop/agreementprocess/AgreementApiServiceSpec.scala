package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementmanagement.client.model.UpdateAgreementSeed
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Creation" should {
    "succeed if all requirements are met" in {
      val descriptor                             = SpecData.publishedDescriptor
      val (eServiceAttributes, tenantAttributes) = SpecData.matchingCertifiedAttributes
      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttributes)
      val consumer = SpecData.tenant.copy(id = requesterOrgId, attributes = Seq(tenantAttributes))
      val payload  = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieve(eService.id, eService)
      mockAgreementsRetrieve(Nil)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementCreation(SpecData.agreement)

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail if EService does not exist" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieveNotFound(eService.id)

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if Descriptor is not in expected state" in {
      val descriptor = SpecData.archivedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if other Agreements exist in conflicting state" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieve(eService.id, eService)
      mockAgreementsRetrieve(Seq(SpecData.agreement))

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail on missing certified attributes" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = SpecData.catalogCertifiedAttribute())
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = Seq(SpecData.tenantCertifiedAttribute()))
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieve(eService.id, eService)
      mockAgreementsRetrieve(Nil)
      mockTenantRetrieve(consumer.id, consumer)

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "Agreement Submission" should {
    "succeed if all requirements are met" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr)
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr)
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.PENDING,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()

      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.submitAgreement(agreementId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if other Agreements exist in conflicting state" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Seq(SpecData.agreement))

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Consumer" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = UUID.randomUUID())

      mockAgreementRetrieve(agreement)

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail if Descriptor is not in expected state" in {
      val descriptor = SpecData.archivedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.draftAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if Agreement is not in expected state" in {
      val agreement = SpecData.pendingAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail and invalidate agreement if tenant lost a certified attribute" in {
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr = SpecData.catalogCertifiedAttribute().copy(declared = eServiceDeclAttr.declared)
      val tenantAttr   = Seq(SpecData.tenantCertifiedAttribute(), tenantDeclAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr)
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr)
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(true)
      )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail on missing declared attributes" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceAttr = SpecData.catalogDeclaredAttribute().copy(certified = eServiceCertAttr.certified)
      val tenantAttr   = Seq(SpecData.tenantDeclaredAttribute(), tenantCertAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr)
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr)
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)

      Get() ~> service.submitAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "Agreement Activation" should {
    "succeed on Pending agreement when Consumer has required attributes" in {
      import AgreementManagement.{CertifiedAttribute, DeclaredAttribute, VerifiedAttribute}

      val certAttr1 = UUID.randomUUID()
      val certAttr2 = UUID.randomUUID()
      val declAttr1 = UUID.randomUUID()
      val declAttr2 = UUID.randomUUID()
      val verAttr1  = UUID.randomUUID()
      val verAttr2  = UUID.randomUUID()

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
        Seq(SpecData.tenantVerifiedAttribute(verAttr1), SpecData.tenantVerifiedAttribute(verAttr2))

      val eServiceAttr =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr   = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr).flatten

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
        state = AgreementManagement.AgreementState.ACTIVE,
        certifiedAttributes = Seq(CertifiedAttribute(certAttr1), CertifiedAttribute(certAttr2)),
        declaredAttributes = Seq(DeclaredAttribute(declAttr1), DeclaredAttribute(declAttr2)),
        verifiedAttributes = Seq(VerifiedAttribute(verAttr1), VerifiedAttribute(verAttr2)),
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(false)
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
        suspendedByPlatform = Some(false)
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
        suspendedByPlatform = Some(false)
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
        suspendedByPlatform = Some(false)
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
        suspendedByPlatform = Some(false)
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
        suspendedByPlatform = Some(true)
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
        suspendedByPlatform = Some(true)
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
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantVerAttr, tenantDeclAttr)

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
        state = AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = Some(false),
        suspendedByPlatform = Some(true)
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
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
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
        suspendedByPlatform = Some(false)
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

  "Agreement Suspension" should {
    "succeed on Active agreement when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.activeAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = None,
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed on Active agreement when requested by Producer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.activeAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed on Suspended agreement (by Consumer) when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.suspendedAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          suspendedByConsumer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = None,
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed on Suspended agreement (by Producer) when requested by Producer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.suspendedAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByProducer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Consumer) when requested by Producer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant
      val agreement  =
        SpecData.suspendedAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByConsumer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Producer) when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.suspendedAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByProducer = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(false)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and remain Suspended on Suspended agreement (by Platform) when requested by Consumer" in {
      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = SpecData.catalogCertifiedAttribute())
      val consumer   = SpecData.tenant.copy(id = requesterOrgId)
      val agreement  =
        SpecData.suspendedAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByPlatform = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = None,
        suspendedByPlatform = Some(true)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
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
        SpecData.suspendedAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          producerId = eService.producerId,
          consumerId = consumer.id,
          suspendedByPlatform = Some(true)
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(true)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and Suspend agreement if tenant lost a certified attribute" in {
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantVerAttr, tenantDeclAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr, producerId = requesterOrgId)
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr)
      val agreement  =
        SpecData.activeAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(true)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and Suspend agreement on missing declared attributes" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantVerAttr, tenantCertAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr, producerId = requesterOrgId)
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr)
      val agreement  =
        SpecData.activeAgreementWithAttributes.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(true)
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()

      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.suspendAgreement(agreementId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if Agreement is not in expected state" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Consumer or the Producer" in {
      val eService  = SpecData.eService.copy(producerId = UUID.randomUUID())
      val agreement = SpecData.activeAgreement.copy(eserviceId = eService.id, consumerId = UUID.randomUUID())

      mockAgreementRetrieve(agreement)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

  }

}
