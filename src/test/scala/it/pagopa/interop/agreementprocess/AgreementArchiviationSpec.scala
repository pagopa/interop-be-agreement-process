package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.events.ArchiveEvent
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Future

class AgreementArchiviationSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Archiviation" should {
    "succeed on agreement when requested by Consumer" in {
      val agreement = SpecData.agreement.copy(consumerId = requesterOrgId)

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
      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementUpdate(agreement.id, expectedSeed, agreement.copy(stamps = SpecData.archiviationStamps))
      (mockAuthorizationManagementService
        .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: ClientComponentState)(_: Seq[(String, String)]))
        .expects(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.INACTIVE, *)
        .once()
        .returns(Future.unit)
      mockArchiveEventSending(ArchiveEvent(agreement.id, now)).twice()

      Get() ~> service.archiveAgreement(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on agreement when requested by Producer" in {
      val agreement = SpecData.agreement.copy(producerId = requesterOrgId)

      mockAgreementRetrieve(agreement.toPersistent)

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
