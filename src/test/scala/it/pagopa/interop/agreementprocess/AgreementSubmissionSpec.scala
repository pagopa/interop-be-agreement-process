package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.model.UpdateAgreementSeed
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.MANUAL
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementSubmissionSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Submission" should {
    "succeed if all requirements are met" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val descriptor = SpecData.publishedDescriptor.copy(agreementApprovalPolicy = MANUAL)
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

}
