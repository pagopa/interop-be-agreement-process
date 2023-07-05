package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.events.ArchiveEvent
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementArchiviationSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Archiviation" should {
    "succeed on agreement when requested by Producer" in {
      val agreement = SpecData.agreement.copy(producerId = requesterOrgId)

      val expectedSeed = AgreementManagement.UpdateAgreementSeed(
        state = AgreementManagement.AgreementState.ARCHIVED,
        certifiedAttributes = Seq.empty,
        declaredAttributes = Seq.empty,
        verifiedAttributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = None,
        stamps = SpecData.archiviationStamps
      )
      val now          = SpecData.when
      mockAgreementRetrieve(agreement.id, agreement)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.archiviationStamps))
      mockArchiveEventSending(ArchiveEvent(agreement.id, now))

      Get() ~> service.archiveAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on agreement when requested by Consumer" in {
      val agreement = SpecData.agreement.copy(consumerId = requesterOrgId)

      mockAgreementRetrieve(agreement.id, agreement)

      Get() ~> service.archiveAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail if requester is not the Consumer or the Producer" in {
      val eService  = SpecData.eService.copy(producerId = UUID.randomUUID())
      val agreement = SpecData.agreement.copy(eserviceId = eService.id, consumerId = UUID.randomUUID())

      mockAgreementRetrieve(agreement.id, agreement)

      Get() ~> service.archiveAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()

      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.archiveAgreement(agreementId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
}
