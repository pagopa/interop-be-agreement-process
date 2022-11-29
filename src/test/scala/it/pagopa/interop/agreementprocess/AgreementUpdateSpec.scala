package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.model.UpgradeAgreementSeed
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientComponentState
}

import java.util.UUID

class AgreementUpdateSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  import agreementApiMarshaller._

  "Agreement update" should {
    "succedd" in {
      val newerDescriptor   = SpecData.publishedDescriptor.copy(version = "10")
      val currentDescriptor = SpecData.deprecatedDescriptor.copy(version = "1")
      val eService          = SpecData.eService.copy(descriptors = Seq(newerDescriptor, currentDescriptor))
      val consumer          = SpecData.tenant.copy(id = requesterOrgId)
      val agreement         =
        SpecData.activeAgreement.copy(
          eserviceId = eService.id,
          descriptorId = currentDescriptor.id,
          consumerId = consumer.id
        )
      val newAgreement      =
        agreement.copy(
          id = UUID.randomUUID(),
          eserviceId = eService.id,
          descriptorId = newerDescriptor.id,
          consumerId = consumer.id
        )

      val seed = UpgradeAgreementSeed(descriptorId = newerDescriptor.id, SpecData.defaultStamp.get)

      mockAgreementRetrieve(agreement)
      mockEServiceRetrieve(eService.id, eService)
      mockAgreementUpgrade(agreement.id, seed, newAgreement)
      mockUpdateAgreementAndEServiceStates(
        eService.id,
        agreement.consumerId,
        ClientAgreementAndEServiceDetailsUpdate(
          agreementId = newAgreement.id,
          agreementState = ClientComponentState.ACTIVE,
          descriptorId = newAgreement.descriptorId,
          audience = newerDescriptor.audience,
          voucherLifespan = newerDescriptor.voucherLifespan,
          eserviceState = ClientComponentState.ACTIVE
        )
      )

      /* | Maybe a trivial test ? |
      val payload = AgreementUpdatePayload("consumer-notes")
      Get() ~> service.updateAgreementById(agreement.id.toString, payload) ~> check {
              status shouldEqual StatusCodes.InternalServerError
      }*/

      Get() ~> service.upgradeAgreementById(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}
