package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.common.Adapters._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

class AgreementDeletionSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Deletion" should {
    "succeed if status is DRAFT" in {
      val consumerDoc1 = SpecData.document()
      val consumerDoc2 = SpecData.document()
      val agreement    =
        SpecData.draftAgreement.copy(consumerId = requesterOrgId, consumerDocuments = Seq(consumerDoc1, consumerDoc2))

      mockAgreementRetrieve(agreement.toPersistent)
      mockFileDeletion(ApplicationConfiguration.storageContainer, consumerDoc1.path)
      mockFileDeletion(ApplicationConfiguration.storageContainer, consumerDoc2.path)
      mockAgreementDeletion(agreement.id)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed if status is MISSING_CERTIFIED_ATTRIBUTES" in {
      val consumerDoc1 = SpecData.document()
      val consumerDoc2 = SpecData.document()
      val agreement    = SpecData.missingCertifiedAttributesAgreement.copy(
        consumerId = requesterOrgId,
        consumerDocuments = Seq(consumerDoc1, consumerDoc2)
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockFileDeletion(ApplicationConfiguration.storageContainer, consumerDoc1.path)
      mockFileDeletion(ApplicationConfiguration.storageContainer, consumerDoc2.path)
      mockAgreementDeletion(agreement.id)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if Agreement does not exist" in {
      val agreement =
        SpecData.draftAgreement.copy(consumerId = requesterOrgId, consumerDocuments = Seq(SpecData.document()))

      mockAgreementRetrieveNotFound(agreement.id)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if requester is not the Consumer" in {
      val agreement =
        SpecData.draftAgreement.copy(producerId = requesterOrgId, consumerDocuments = Seq(SpecData.document()))

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail if Agreement is not in allowed state" in {
      val agreement =
        SpecData.activeAgreement.copy(consumerId = requesterOrgId, consumerDocuments = Seq(SpecData.document()))

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.deleteAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

}
