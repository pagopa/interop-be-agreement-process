package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.SpecData.catalogCertifiedAttribute
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.common.Adapters._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementCreationSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Creation" should {
    "succeed if all requirements are met" in {
      val (descriptorAttributes, tenantAttributes) = SpecData.matchingCertifiedAttributes
      val descriptor = SpecData.publishedDescriptor.copy(attributes = descriptorAttributes)
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = List(tenantAttributes))
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieve(eService.id, eService)
      mockAgreementsRetrieve(Nil)
      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementCreation(SpecData.agreement)

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "succeed if Consumer and Producer are the same even on unmet attributes" in {
      val eServiceCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr   = SpecData.tenantCertifiedAttribute()
      val eServiceDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr   = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr  = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr    = SpecData.tenantVerifiedAttribute()
      val descriptorAttr   =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr       = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val descriptor = SpecData.publishedDescriptor.copy(attributes = descriptorAttr)
      val eService   =
        SpecData.eService.copy(producerId = requesterOrgId, descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = tenantAttr)
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

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

    "fail if Descriptor is not in published state" in {
      val attributeId          = UUID.randomUUID()
      val descriptorAttributes = catalogCertifiedAttribute(attributeId)
      val descriptor           = SpecData.draftDescriptor.copy(attributes = descriptorAttributes)
      val eService             = SpecData.eService.copy(descriptors = Seq(descriptor))
      val payload              = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

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
      mockAgreementsRetrieve(Seq(SpecData.agreement.toPersistent))

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.Conflict
      }
    }

    "fail on missing certified attributes" in {
      val descriptor = SpecData.publishedDescriptor.copy(attributes = SpecData.catalogCertifiedAttribute())
      val eService   = SpecData.eService.copy(descriptors = Seq(descriptor))
      val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = List(SpecData.tenantCertifiedAttribute()))
      val payload    = AgreementPayload(eserviceId = eService.id, descriptorId = descriptor.id)

      mockEServiceRetrieve(eService.id, eService)
      mockAgreementsRetrieve(Nil)
      mockTenantRetrieve(consumer.id, consumer)

      Get() ~> service.createAgreement(payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

}
