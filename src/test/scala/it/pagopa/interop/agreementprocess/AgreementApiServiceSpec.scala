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

//  "Agreement Activation" should {
//    "succeed on pending agreement" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val pendingAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.PENDING)
//      val eService         = TestDataOne.eService.copy(descriptors =
//        Seq(
//          EServiceDescriptor(
//            id = pendingAgreement.descriptorId,
//            version = "1",
//            description = None,
//            audience = Seq.empty,
//            voucherLifespan = 0,
//            interface = None,
//            docs = Seq.empty,
//            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
//            dailyCallsPerConsumer = 1000,
//            dailyCallsTotal = 10
//          )
//        )
//      )
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(pendingAgreement))
//
//      (
//        mockAgreementManagementService
//          .getAgreements(
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[AgreementManagementDependency.AgreementState]
//          )(_: Seq[(String, String)])
//        )
//        .expects(
//          Some(TestDataOne.producerId.toString),
//          Some(pendingAgreement.consumerId.toString),
//          Some(eService.id.toString),
//          Some(TestDataOne.descriptorId.toString),
//          Some(AgreementManagementDependency.AgreementState.ACTIVE),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      (mockPartyManagementService
//        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
//        .expects(pendingAgreement.consumerId, *, *)
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      (mockCatalogManagementService
//        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
//        .expects(eService.id, Common.requestContexts)
//        .once()
//        .returns(Future.successful(eService))
//
//      (mockAgreementManagementService
//        .activateById(_: String, _: StateChangeDetails)(_: Seq[(String, String)]))
//        .expects(
//          TestDataOne.id.toString,
//          StateChangeDetails(changedBy = Some(AgreementManagementDependency.ChangedBy.PRODUCER)),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(pendingAgreement))
//
//      (
//        mockAuthorizationManagementService
//          .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: AuthorizationManagementDependency.ClientComponentState)(
//            _: Seq[(String, String)]
//          )
//        )
//        .expects(
//          pendingAgreement.eserviceId,
//          pendingAgreement.consumerId,
//          pendingAgreement.id,
//          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
//          Common.requestContexts
//        )
//        .returning(Future.successful(()))
//        .once()
//
//      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.NoContent
//      }
//    }
//
//    "succeed on suspended agreement" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val suspendedAgreement                      =
//        TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)
//      val eService                                = TestDataOne.eService.copy(descriptors =
//        Seq(
//          EServiceDescriptor(
//            id = suspendedAgreement.descriptorId,
//            version = "1",
//            description = None,
//            audience = Seq.empty,
//            voucherLifespan = 0,
//            interface = None,
//            docs = Seq.empty,
//            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
//            dailyCallsPerConsumer = 1000,
//            dailyCallsTotal = 10
//          )
//        )
//      )
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(suspendedAgreement))
//
//      (
//        mockAgreementManagementService
//          .getAgreements(
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[AgreementManagementDependency.AgreementState]
//          )(_: Seq[(String, String)])
//        )
//        .expects(
//          Some(TestDataOne.producerId.toString),
//          Some(suspendedAgreement.consumerId.toString),
//          Some(eService.id.toString),
//          Some(TestDataOne.descriptorId.toString),
//          Some(AgreementManagementDependency.AgreementState.ACTIVE),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      (mockPartyManagementService
//        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
//        .expects(suspendedAgreement.consumerId, *, *)
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      (mockCatalogManagementService
//        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
//        .expects(eService.id, Common.requestContexts)
//        .once()
//        .returns(Future.successful(eService))
//
//      (mockAgreementManagementService
//        .activateById(_: String, _: StateChangeDetails)(_: Seq[(String, String)]))
//        .expects(
//          TestDataOne.id.toString,
//          StateChangeDetails(changedBy = Some(AgreementManagementDependency.ChangedBy.PRODUCER)),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(suspendedAgreement))
//
//      (
//        mockAuthorizationManagementService
//          .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: AuthorizationManagementDependency.ClientComponentState)(
//            _: Seq[(String, String)]
//          )
//        )
//        .expects(
//          suspendedAgreement.eserviceId,
//          suspendedAgreement.consumerId,
//          suspendedAgreement.id,
//          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
//          Common.requestContexts
//        )
//        .returning(Future.successful(()))
//        .once()
//
//      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.NoContent
//      }
//    }
//
//    "fail if missing authorization header" in {
//      implicit val contexts: Seq[(String, String)] = Seq.empty[(String, String)]
//      val service: AgreementApiService             = AgreementApiServiceImpl(
//        AgreementManagementServiceImpl(
//          AgreementManagementInvoker(ExecutionContext.global),
//          AgreementManagementApi(url)
//        ),
//        mockCatalogManagementService,
//        mockPartyManagementService,
//        mockAttributeManagementService,
//        mockAuthorizationManagementService,
//        mockJWTReader
//      )(ExecutionContext.global)
//      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.Forbidden
//      }
//    }
//
//    "fail if an active agreement already exists" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)
//      val activeAgreement  =
//        TestDataOne.agreement.copy(id = UUID.randomUUID(), state = AgreementManagementDependency.AgreementState.ACTIVE)
//
//      val eService = TestDataOne.eService.copy(descriptors =
//        Seq(
//          EServiceDescriptor(
//            id = currentAgreement.descriptorId,
//            version = "1",
//            description = None,
//            audience = Seq.empty,
//            voucherLifespan = 0,
//            interface = None,
//            docs = Seq.empty,
//            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
//            dailyCallsPerConsumer = 1000,
//            dailyCallsTotal = 10
//          )
//        )
//      )
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(currentAgreement))
//
//      (
//        mockAgreementManagementService
//          .getAgreements(
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[AgreementManagementDependency.AgreementState]
//          )(_: Seq[(String, String)])
//        )
//        .expects(
//          Some(TestDataOne.producerId.toString),
//          Some(currentAgreement.consumerId.toString),
//          Some(eService.id.toString),
//          Some(TestDataOne.descriptorId.toString),
//          Some(AgreementManagementDependency.AgreementState.ACTIVE),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(Seq(activeAgreement)))
//
//      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.BadRequest
//      }
//    }
//
//    "fail if agreement is not Pending or Suspended" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.ACTIVE)
//
//      val eService = TestDataOne.eService.copy(descriptors =
//        Seq(
//          EServiceDescriptor(
//            id = currentAgreement.descriptorId,
//            version = "1",
//            description = None,
//            audience = Seq.empty,
//            voucherLifespan = 0,
//            interface = None,
//            docs = Seq.empty,
//            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
//            dailyCallsPerConsumer = 1000,
//            dailyCallsTotal = 10
//          )
//        )
//      )
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(currentAgreement))
//
//      (
//        mockAgreementManagementService
//          .getAgreements(
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[AgreementManagementDependency.AgreementState]
//          )(_: Seq[(String, String)])
//        )
//        .expects(
//          Some(TestDataOne.producerId.toString),
//          Some(currentAgreement.consumerId.toString),
//          Some(eService.id.toString),
//          Some(TestDataOne.descriptorId.toString),
//          Some(AgreementManagementDependency.AgreementState.ACTIVE),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.BadRequest
//      }
//    }
//
//    "fail if descriptor is not Published" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)
//
//      val eService = TestDataOne.eService.copy(descriptors =
//        Seq(
//          EServiceDescriptor(
//            id = currentAgreement.descriptorId,
//            version = "1",
//            description = None,
//            audience = Seq.empty,
//            voucherLifespan = 0,
//            interface = None,
//            docs = Seq.empty,
//            state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED,
//            dailyCallsPerConsumer = 1000,
//            dailyCallsTotal = 10
//          )
//        )
//      )
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(currentAgreement))
//
//      (
//        mockAgreementManagementService
//          .getAgreements(
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[String],
//            _: Option[AgreementManagementDependency.AgreementState]
//          )(_: Seq[(String, String)])
//        )
//        .expects(
//          Some(TestDataOne.producerId.toString),
//          Some(currentAgreement.consumerId.toString),
//          Some(eService.id.toString),
//          Some(TestDataOne.descriptorId.toString),
//          Some(AgreementManagementDependency.AgreementState.ACTIVE),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      (mockPartyManagementService
//        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
//        .expects(currentAgreement.consumerId, *, *)
//        .once()
//        .returns(Future.successful(Seq.empty))
//
//      (mockCatalogManagementService
//        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
//        .expects(eService.id, Common.requestContexts)
//        .once()
//        .returns(Future.successful(eService))
//
//      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.BadRequest
//      }
//    }
//
//  }
//
//  "Agreement Suspension" should {
//    "succeed on active agreement" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val activeAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.ACTIVE)
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(activeAgreement))
//
//      (mockAgreementManagementService
//        .suspendById(_: String, _: StateChangeDetails)(_: Seq[(String, String)]))
//        .expects(
//          TestDataOne.id.toString,
//          StateChangeDetails(Some(AgreementManagementDependency.ChangedBy.PRODUCER)),
//          Common.requestContexts
//        )
//        .once()
//        .returns(Future.successful(activeAgreement))
//
//      (
//        mockAuthorizationManagementService
//          .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: AuthorizationManagementDependency.ClientComponentState)(
//            _: Seq[(String, String)]
//          )
//        )
//        .expects(
//          activeAgreement.eserviceId,
//          activeAgreement.consumerId,
//          activeAgreement.id,
//          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
//          Common.requestContexts
//        )
//        .returning(Future.successful(()))
//        .once()
//
//      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.NoContent
//      }
//    }
//
//    "fail if agreement is not Active" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataOne.id.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(currentAgreement))
//
//      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
//        status shouldEqual StatusCodes.BadRequest
//      }
//    }
//  }
//
//  "Agreement Retrieve" should {
//    "retrieves an agreement" in {
//      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
//
//      (mockAgreementManagementService
//        .getAgreementById(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataSeven.agreementId.toString, Common.requestContexts)
//        .once()
//        .returns(Future.successful(TestDataSeven.agreement))
//
//      (mockCatalogManagementService
//        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
//        .expects(TestDataSeven.eservice.id, Common.requestContexts)
//        .once()
//        .returns(Future.successful(TestDataSeven.eservice))
//
//      (mockPartyManagementService
//        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
//        .expects(TestDataSeven.producerId, *, *)
//        .once()
//        .returns(Future.successful(TestDataSeven.producer))
//
//      (mockPartyManagementService
//        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
//        .expects(TestDataSeven.consumerId, *, *)
//        .once()
//        .returns(Future.successful(TestDataSeven.consumer))
//
//      (mockAttributeManagementService
//        .getAttribute(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataSeven.eservice.attributes.verified.head.single.get.id, Common.requestContexts)
//        .once()
//        .returns(Future.successful(ClientAttributes.verifiedAttributeId1))
//
//      (mockAttributeManagementService
//        .getAttribute(_: String)(_: Seq[(String, String)]))
//        .expects(TestDataSeven.eservice.attributes.verified(1).single.get.id, Common.requestContexts)
//        .once()
//        .returns(Future.successful(ClientAttributes.verifiedAttributeId2))
//
//      import agreementApiMarshaller._
//      import spray.json.DefaultJsonProtocol._
//
//      implicit def organizationJsonFormat: RootJsonFormat[Organization]               = jsonFormat2(Organization)
//      implicit def activeDescriptorJsonFormat: RootJsonFormat[ActiveDescriptor]       = jsonFormat3(ActiveDescriptor)
//      implicit def eServiceJsonFormat: RootJsonFormat[EService]                       = jsonFormat4(EService)
//      implicit def attributeJsonFormat: RootJsonFormat[Attribute]                     = jsonFormat9(Attribute)
//      implicit def agreementAttributesJsonFormat: RootJsonFormat[AgreementAttributes] = jsonFormat2(AgreementAttributes)
//      implicit def agreementJsonFormat: RootJsonFormat[Agreement]                     = jsonFormat9(Agreement)
//      import SprayJsonSupport.sprayJsonUnmarshaller
//
//      implicit def fromEntityUnmarshallerAgreement: FromEntityUnmarshaller[Agreement] =
//        sprayJsonUnmarshaller[Agreement]
//
//      val expected = Agreement(
//        id = TestDataSeven.agreementId,
//        producer = Organization(id = TestDataSeven.producer.originId, name = TestDataSeven.producer.description),
//        consumer = Organization(id = TestDataSeven.consumer.originId, name = TestDataSeven.consumer.description),
//        eservice = EService(
//          id = TestDataSeven.eservice.id,
//          name = TestDataSeven.eservice.name,
//          version = TestDataSeven.eservice.descriptors.head.version,
//          activeDescriptor = None
//        ),
//        eserviceDescriptorId = TestDataSeven.eservice.descriptors.head.id,
//        state = agreementStateToApi(TestDataSeven.agreement.state),
//        suspendedByConsumer = None,
//        suspendedByProducer = None,
//        attributes = Seq(
//          AgreementAttributes(
//            single = Some(
//              Attribute(
//                id = UUID.fromString(ClientAttributes.verifiedAttributeId1.id),
//                code = ClientAttributes.verifiedAttributeId1.code,
//                description = ClientAttributes.verifiedAttributeId1.description,
//                origin = ClientAttributes.verifiedAttributeId1.origin,
//                name = ClientAttributes.verifiedAttributeId1.name,
//                explicitAttributeVerification =
//                  Some(TestDataSeven.eservice.attributes.verified.head.single.get.explicitAttributeVerification),
//                verified = TestDataSeven.agreement.verifiedAttributes.head.verified,
//                verificationDate = TestDataSeven.agreement.verifiedAttributes.head.verificationDate,
//                validityTimespan = TestDataSeven.agreement.verifiedAttributes.head.validityTimespan
//              )
//            ),
//            group = None
//          ),
//          AgreementAttributes(
//            single = Some(
//              Attribute(
//                id = UUID.fromString(ClientAttributes.verifiedAttributeId2.id),
//                code = ClientAttributes.verifiedAttributeId2.code,
//                description = ClientAttributes.verifiedAttributeId2.description,
//                origin = ClientAttributes.verifiedAttributeId2.origin,
//                name = ClientAttributes.verifiedAttributeId2.name,
//                explicitAttributeVerification =
//                  Some(TestDataSeven.eservice.attributes.verified(1).single.get.explicitAttributeVerification),
//                verified = TestDataSeven.agreement.verifiedAttributes(1).verified,
//                verificationDate = TestDataSeven.agreement.verifiedAttributes(1).verificationDate,
//                validityTimespan = TestDataSeven.agreement.verifiedAttributes(1).validityTimespan
//              )
//            ),
//            group = None
//          )
//        )
//      )
//
//      Get() ~> service.getAgreementById(TestDataSeven.agreementId.toString) ~> check {
//        status shouldEqual StatusCodes.OK
//        responseAs[Agreement] shouldEqual expected
//      }
//    }
//
//  }

}
