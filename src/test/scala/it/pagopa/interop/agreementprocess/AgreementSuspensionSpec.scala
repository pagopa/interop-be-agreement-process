package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.model.UpdateAgreementSeed
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementSuspensionSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

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
        suspendedByPlatform = Some(false),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
        suspendedByPlatform = Some(false),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
          suspendedByConsumer = Some(true),
          suspendedByProducer = None,
          suspendedByPlatform = None
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
        suspendedByPlatform = Some(false),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
        suspendedByPlatform = Some(false),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
        suspendedByPlatform = Some(false),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
          suspendedByPlatform = Some(true),
          suspendedByProducer = None,
          suspendedByConsumer = None
        )

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.SUSPENDED,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = Some(true),
        suspendedByProducer = None,
        suspendedByPlatform = Some(true),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
        suspendedByPlatform = Some(true),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and Suspend agreement if tenant lost a certified attribute" in {
      val producerId                         = requesterOrgId
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
        suspendedByPlatform = Some(true),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
      mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.suspendAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and Suspend agreement on missing declared attributes" in {
      val producerId                         = requesterOrgId
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantVerAttr, tenantCertAttr)

      val descriptor = SpecData.publishedDescriptor
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttr, producerId = producerId)
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
        suspendedByPlatform = Some(true),
        stamps = SpecData.suspensionStamps
      )

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.suspensionStamps))
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
