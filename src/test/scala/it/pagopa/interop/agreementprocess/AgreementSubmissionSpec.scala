package it.pagopa.interop.agreementprocess

import cats.syntax.all._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.client.model.{
  CertifiedAttribute,
  DeclaredAttribute,
  UpdateAgreementSeed,
  VerifiedAttribute
}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.model.AgreementSubmissionPayload
import it.pagopa.interop.catalogmanagement.model.{Manual, Automatic}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementSubmissionSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Submission" should {
    "succeed if all requirements are met" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val descriptorAttr                     = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = List(tenantCertAttr, tenantDeclAttr)

      val descriptor =
        SpecData.publishedDescriptor.copy(agreementApprovalPolicy = Manual.some, attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   =
        SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr, mails = List(SpecData.validEmail))
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.PENDING,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        consumerNotes = payload.consumerNotes,
        stamps = SpecData.submissionStamps
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Nil)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockTenantRetrieve(agreement.consumerId, consumer)
      mockAgreementUpdate(
        agreement.id,
        expectedSeed,
        agreement.copy(state = expectedSeed.state, stamps = SpecData.submissionStamps)
      )
      // if we decide to update the client even if the agreement is not active or suspended, uncomment this mock
      // mockClientStateUpdate(eService.id, consumer.id, agreement.id, ClientComponentState.INACTIVE)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed and Activate if all requirements are met and approval policy is Automatic" in {
      val producerId: UUID = requesterOrgId

      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVeriAttr, tenantVeriAttr) = SpecData.matchingVerifiedAttributes(producerId)
      val descriptorAttr                     =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVeriAttr.verified)
      val tenantAttr                         = List(tenantCertAttr, tenantDeclAttr, tenantVeriAttr)

      val descriptor =
        SpecData.publishedDescriptor.copy(agreementApprovalPolicy = Automatic.some, attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor), producerId = producerId)
      val consumer   =
        SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr, mails = List(SpecData.validEmail))
      val producer   = SpecData.tenant.copy(id = producerId)
      val agreement  =
        SpecData.draftAgreement.copy(
          producerId = producerId,
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id
        )
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.ACTIVE,
        certifiedAttributes = Seq(CertifiedAttribute(tenantCertAttr.id)),
        declaredAttributes = Seq(DeclaredAttribute(tenantDeclAttr.id)),
        verifiedAttributes = Seq(VerifiedAttribute(tenantVeriAttr.id)),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        consumerNotes = payload.consumerNotes,
        stamps = SpecData.activationStamps
      )

      mockAutomaticActivation(agreement.toPersistent, eService, consumer, producer, expectedSeed)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed if Consumer is the Producer, even if EService attributes are not met" in {
      val eServiceCertAttr = SpecData.catalogCertifiedAttribute()
      val eServiceDeclAttr = SpecData.catalogDeclaredAttribute()
      val eServiceVerAttr  = SpecData.catalogVerifiedAttribute()
      val tenantCertAttr   = SpecData.tenantCertifiedAttribute()
      val tenantDeclAttr   = SpecData.tenantDeclaredAttribute()
      val tenantVerAttr    = SpecData.tenantVerifiedAttribute()

      val descriptorAttr =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr     = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val consumerAndProducer = requesterOrgId
      val descriptor          =
        SpecData.publishedDescriptor.copy(agreementApprovalPolicy = Manual.some, attributes = descriptorAttr)
      val eService            =
        SpecData.eService.copy(producerId = consumerAndProducer, descriptors = Seq(descriptor))
      val consumer            =
        SpecData.tenant.copy(id = consumerAndProducer, attributes = tenantAttr, mails = List(SpecData.validEmail))
      val producer            = consumer
      val agreement           =
        SpecData.draftAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )
      val payload             = AgreementSubmissionPayload(Some("consumer-notes"))

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.ACTIVE,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        consumerNotes = payload.consumerNotes,
        stamps = SpecData.activationStamps
      )

      mockSelfActivation(agreement.toPersistent, eService, consumer, producer, expectedSeed)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()
      val payload     = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.submitAgreement(agreementId.toString, payload) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if other Agreements exist in conflicting state" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = requesterOrgId)
      val payload   = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Seq(SpecData.agreement.toPersistent))

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.Conflict
      }
    }

    "fail if agreement has a consumer without contact email" in {

      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val descriptorAttr                     = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = List(tenantCertAttr, tenantDeclAttr)

      val descriptor =
        SpecData.publishedDescriptor.copy(agreementApprovalPolicy = Manual.some, attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   =
        SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr, mails = Nil)
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockTenantRetrieve(agreement.consumerId, consumer)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Consumer" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = UUID.randomUUID())
      val payload   = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
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
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if Descriptor is not in suspended or published state" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val descriptorAttr                     = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = List(tenantCertAttr, tenantDeclAttr)

      val descriptor = SpecData.draftDescriptor.copy(agreementApprovalPolicy = Manual.some, attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr)
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if Agreement is not in expected state" in {
      val agreement = SpecData.pendingAgreement.copy(consumerId = requesterOrgId)
      val payload   = AgreementSubmissionPayload(Some("consumer-notes"))

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail and invalidate agreement if tenant lost a certified attribute" in {
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val descriptorAttr = SpecData.catalogCertifiedAttribute().copy(declared = eServiceDeclAttr.declared)
      val tenantAttr     = List(SpecData.tenantCertifiedAttribute(), tenantDeclAttr)

      val descriptor = SpecData.publishedDescriptor.copy(attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   =
        SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr, mails = List(SpecData.validEmail))
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES,
        certifiedAttributes = Nil,
        declaredAttributes = Nil,
        verifiedAttributes = Nil,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(true),
        consumerNotes = payload.consumerNotes,
        stamps = agreement.stamps
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Nil)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockTenantRetrieve(agreement.consumerId, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail on missing declared attributes" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val descriptorAttr = SpecData.catalogDeclaredAttribute().copy(certified = eServiceCertAttr.certified)
      val tenantAttr     = List(SpecData.tenantDeclaredAttribute(), tenantCertAttr)

      val descriptor = SpecData.publishedDescriptor.copy(attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   =
        SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr, mails = List(SpecData.validEmail))
      val agreement  =
        SpecData.draftAgreement.copy(eserviceId = eService.id, descriptorId = descriptor.id, consumerId = consumer.id)
      val payload    = AgreementSubmissionPayload(Some("consumer-notes"))

      val expectedSeed = UpdateAgreementSeed(
        state = agreement.state,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = agreement.suspendedByConsumer,
        suspendedByProducer = agreement.suspendedByProducer,
        suspendedByPlatform = Some(false),
        consumerNotes = payload.consumerNotes,
        stamps = agreement.stamps
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementsRetrieve(Nil)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockTenantRetrieve(agreement.consumerId, consumer)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement)

      Get() ~> service.submitAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

}
