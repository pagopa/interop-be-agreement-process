package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

class AgreementDeletionSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Deletion" should {
    "succeed if status is DRAFT" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)
      mockAgreementDeletion(agreement.id)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed if status is MISSING_CERTIFIED_ATTRIBUTES" in {
      val agreement = SpecData.missingCertifiedAttributesAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)
      mockAgreementDeletion(agreement.id)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if Agreement does not exist" in {
      val agreement = SpecData.draftAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieveNotFound(agreement.id)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if requester is not the Consumer" in {
      val agreement = SpecData.draftAgreement.copy(producerId = requesterOrgId)

      mockAgreementRetrieve(agreement)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail if Agreement is not in allowed state" in {
      val agreement = SpecData.activeAgreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

}
