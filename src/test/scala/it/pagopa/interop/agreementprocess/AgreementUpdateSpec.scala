package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.client.model.UpdateAgreementSeed
import it.pagopa.interop.agreementprocess.model.AgreementUpdatePayload
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementUpdateSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement Update" should {
    "succeed if agreement is in draft" in {

      val eServiceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()

      val payload = AgreementUpdatePayload("consumer-notes")

      val agreement =
        SpecData.draftAgreement.copy(
          eserviceId = eServiceId,
          descriptorId = descriptorId,
          consumerId = requesterOrgId,
          producerId = producerId,
          consumerNotes = Some("old-consumer-notes")
        )

      val updatedAgreement =
        agreement.copy(consumerNotes = Some(payload.consumerNotes))

      val seed = UpdateAgreementSeed(
        state = agreement.state,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = agreement.suspendedByConsumer,
        suspendedByProducer = agreement.suspendedByProducer,
        suspendedByPlatform = agreement.suspendedByPlatform,
        consumerNotes = Some(payload.consumerNotes),
        stamps = agreement.stamps
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementUpdate(agreement.id, seed, updatedAgreement)

      Get() ~> service.updateAgreementById(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Agreement Update" should {
    "fail if agreement is not in draft" in {

      val eServiceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()

      val payload = AgreementUpdatePayload("consumer-notes")

      val agreement =
        SpecData.activeAgreement.copy(
          eserviceId = eServiceId,
          descriptorId = descriptorId,
          consumerId = requesterOrgId,
          producerId = producerId,
          consumerNotes = Some("old-consumer-notes")
        )

      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.updateAgreementById(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "Agreement Update" should {
    "fail if requester is not the producerId" in {

      val eServiceId   = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()
      val producerId   = UUID.randomUUID()
      val requester    = UUID.randomUUID()
      val payload      = AgreementUpdatePayload("consumer-notes")

      val agreement =
        SpecData.draftAgreement.copy(
          eserviceId = eServiceId,
          descriptorId = descriptorId,
          consumerId = requesterOrgId,
          producerId = producerId,
          consumerNotes = Some("old-consumer-notes")
        )

      val updatedAgreement =
        agreement.copy(consumerNotes = Some(payload.consumerNotes), producerId = requester)

      val seed = UpdateAgreementSeed(
        state = agreement.state,
        certifiedAttributes = agreement.certifiedAttributes,
        declaredAttributes = agreement.declaredAttributes,
        verifiedAttributes = agreement.verifiedAttributes,
        suspendedByConsumer = agreement.suspendedByConsumer,
        suspendedByProducer = agreement.suspendedByProducer,
        suspendedByPlatform = agreement.suspendedByPlatform,
        consumerNotes = Some(payload.consumerNotes),
        stamps = agreement.stamps
      )

      mockAgreementRetrieve(agreement.toPersistent)
      mockAgreementUpdate(agreement.id, seed, updatedAgreement)

      Get() ~> service.updateAgreementById(agreement.id.toString, payload) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}
