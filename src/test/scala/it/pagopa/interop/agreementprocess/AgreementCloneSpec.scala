package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiMarshallerImpl._

import java.util.UUID

class AgreementCloneSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Agreement Clone" should {
    "succeed on Rejected agreement when requested by Consumer" in {

      val descriptor                             = SpecData.publishedDescriptor
      val (eServiceAttributes, tenantAttributes) = SpecData.matchingCertifiedAttributes
      val eService = SpecData.eService.copy(descriptors = Seq(descriptor), attributes = eServiceAttributes)
      val consumer = SpecData.tenant.copy(id = requesterOrgId, attributes = List(tenantAttributes))

      val consumerDoc1 = SpecData.persistentDocument()
      val consumerDoc2 = SpecData.persistentDocument()

      val agreement =
        SpecData.rejectedAgreement.copy(
          eserviceId = UUID.randomUUID(),
          descriptorId = UUID.randomUUID(),
          consumerId = requesterOrgId,
          producerId = UUID.randomUUID(),
          consumerDocuments = Seq(consumerDoc1, consumerDoc2)
        )

      mockAgreementRetrieve(agreement)
      mockAgreementsRetrieve(Nil)
      mockEServiceRetrieve(agreement.eserviceId, eService)
      mockTenantRetrieve(consumer.id, consumer)

      mockFileCopy
      mockFileCopy

      mockAddConsumerDocument
      mockAddConsumerDocument

      mockAgreementCreation(SpecData.draftAgreement)

      Get() ~> service.cloneAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

  }
  "fail if agreement to clone does not exists" in {

    val agreement =
      SpecData.rejectedAgreement.copy(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = requesterOrgId,
        producerId = UUID.randomUUID()
      )

    mockAgreementRetrieveNotFound(UUID.randomUUID())

    Get() ~> service.cloneAgreement(agreement.id.toString) ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  "fail if EService does not exist" in {

    val agreement =
      SpecData.rejectedAgreement.copy(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = requesterOrgId,
        producerId = UUID.randomUUID()
      )

    mockAgreementRetrieve(agreement)
    mockEServiceRetrieveNotFound(agreement.eserviceId)

    Get() ~> service.cloneAgreement(agreement.id.toString) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "fail on missing certified attributes" in {

    val agreement =
      SpecData.rejectedAgreement.copy(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = requesterOrgId,
        producerId = UUID.randomUUID()
      )

    val descriptor = SpecData.publishedDescriptor
    val eService   =
      SpecData.eService.copy(descriptors = Seq(descriptor), attributes = SpecData.catalogCertifiedAttribute())
    val consumer   = SpecData.tenant.copy(id = requesterOrgId, attributes = List(SpecData.tenantCertifiedAttribute()))

    mockEServiceRetrieve(agreement.eserviceId, eService)
    mockAgreementsRetrieve(Nil)
    mockAgreementRetrieve(agreement)
    mockTenantRetrieve(consumer.id, consumer)

    Get() ~> service.cloneAgreement(agreement.id.toString) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "fail if agreement to clone is not in REJECTED state" in {

    val agreement =
      SpecData.activeAgreement.copy(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = requesterOrgId,
        producerId = UUID.randomUUID()
      )

    mockAgreementRetrieve(agreement)

    Get() ~> service.cloneAgreement(agreement.id.toString) ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  "fail if agreement to clone is not requested by consumer" in {

    val agreement =
      SpecData.rejectedAgreement.copy(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        producerId = UUID.randomUUID()
      )

    mockAgreementRetrieve(agreement)

    Get() ~> service.cloneAgreement(agreement.id.toString) ~> check {
      status shouldEqual StatusCodes.Forbidden
    }
  }

}
