package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementCloneSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Clone" should {
    "succeed on Rejected agreement when requested by Consumer" in {

      val agreement =
        SpecData.rejectedAgreement.copy(
          eserviceId = UUID.randomUUID(),
          descriptorId = UUID.randomUUID(),
          consumerId = requesterOrgId,
          producerId = UUID.randomUUID()
        )

      mockAgreementRetrieve(agreement)

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
