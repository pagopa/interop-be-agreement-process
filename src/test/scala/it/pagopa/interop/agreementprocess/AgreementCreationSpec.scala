package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.model._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

class AgreementCreationSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

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

}
