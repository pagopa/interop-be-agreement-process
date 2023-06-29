package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.agreementmanagement.client.model.{
  CertifiedAttribute,
  DeclaredAttribute,
  UpdateAgreementSeed,
  VerifiedAttribute
}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.model.AgreementRejectionPayload
import it.pagopa.interop.agreementprocess.common.Adapters._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementRejectionSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Rejection" should {
    "succeed on Pending agreement when requested by Producer" in {
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
        Seq(
          SpecData.tenantVerifiedAttribute(verAttr1, requesterOrgId),
          SpecData.tenantVerifiedAttribute(verAttr2, requesterOrgId)
        )

      val descriptorAttr =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr     = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr).flatten

      val descriptor = SpecData.publishedDescriptor.copy(attributes = descriptorAttr)
      val eService   =
        SpecData.eService.copy(descriptors = Seq(descriptor), producerId = requesterOrgId)
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr)

      val agreement =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val rejectionReason = "Document not valid"

      val payload = AgreementRejectionPayload(rejectionReason)

      val expectedSeed = UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.REJECTED,
        certifiedAttributes = Seq(CertifiedAttribute(certAttr1), CertifiedAttribute(certAttr2)),
        declaredAttributes = Seq(DeclaredAttribute(declAttr1), DeclaredAttribute(declAttr2)),
        verifiedAttributes = Seq(VerifiedAttribute(verAttr1), VerifiedAttribute(verAttr2)),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = None,
        stamps = SpecData.rejectionStamps,
        rejectionReason = rejectionReason.some
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockEServiceRetrieve(eService.id, eService)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementUpdate(
        agreement.id,
        expectedSeed,
        agreement.copy(stamps = SpecData.rejectionStamps, rejectionReason = rejectionReason.some)
      )

      Get() ~> service.rejectAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on Pending agreement when requested by Consumer" in {
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

      val descriptorAttr =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr     = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr).flatten

      val descriptor = SpecData.publishedDescriptor.copy(attributes = descriptorAttr)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(attributes = tenantAttr, id = requesterOrgId)

      val agreement =
        SpecData.pendingAgreement.copy(
          eserviceId = eService.id,
          descriptorId = descriptor.id,
          consumerId = consumer.id,
          producerId = eService.producerId
        )

      val payload = AgreementRejectionPayload("Document not valid")

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.rejectAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail if requester is not the Consumer or the Producer" in {
      val eService  = SpecData.eService.copy(producerId = UUID.randomUUID())
      val agreement = SpecData.pendingAgreement.copy(eserviceId = eService.id, consumerId = UUID.randomUUID())
      val payload   = AgreementRejectionPayload("Document not valid")

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.rejectAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()
      val payload     = AgreementRejectionPayload("Document not valid")

      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.rejectAgreement(agreementId.toString, payload) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if Agreement is not in expected state" in {
      val agreement = SpecData.activeAgreement.copy(producerId = requesterOrgId)
      val payload   = AgreementRejectionPayload("Document not valid")

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.rejectAgreement(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

}
