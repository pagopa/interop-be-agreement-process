package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.client.model.UpgradeAgreementSeed
import it.pagopa.interop.agreementprocess.events.ArchiveEvent
import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientComponentState
}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementUpgradeSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {
  import agreementApiMarshaller._

  "Agreement Upgrade" should {
    "succeed" in {
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

      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementRetrieve(agreement.toPersistent)
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
      mockArchiveEventSending(ArchiveEvent(agreement.id, SpecData.when))

      Get() ~> service.upgradeAgreementById(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail on missing agreement" in {
      val agreementId = UUID.randomUUID()
      val consumer    = SpecData.tenant.copy(id = requesterOrgId)

      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementRetrieveNotFound(agreementId)

      Get() ~> service.upgradeAgreementById(agreementId.toString) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "fail if no Published descriptor exists" in {
      val currentDescriptor = SpecData.deprecatedDescriptor.copy(version = "1")
      val eService          = SpecData.eService.copy(descriptors = Seq(currentDescriptor))
      val consumer          = SpecData.tenant.copy(id = requesterOrgId)
      val agreement         =
        SpecData.activeAgreement.copy(
          eserviceId = eService.id,
          descriptorId = currentDescriptor.id,
          consumerId = consumer.id
        )

      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementRetrieve(agreement.toPersistent)
      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.upgradeAgreementById(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if Agreement is not in expected state" in {
      val newerDescriptor   = SpecData.publishedDescriptor.copy(version = "10")
      val currentDescriptor = SpecData.deprecatedDescriptor.copy(version = "1")
      val eService          = SpecData.eService.copy(descriptors = Seq(newerDescriptor, currentDescriptor))
      val consumer          = SpecData.tenant.copy(id = requesterOrgId)
      val agreement         =
        SpecData.draftAgreement.copy(
          eserviceId = eService.id,
          descriptorId = currentDescriptor.id,
          consumerId = consumer.id
        )

      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.upgradeAgreementById(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if not newer descriptor exists" in {
      val currentDescriptor = SpecData.publishedDescriptor.copy(version = "1")
      val eService          = SpecData.eService.copy(descriptors = Seq(currentDescriptor))
      val consumer          = SpecData.tenant.copy(id = requesterOrgId)
      val agreement         =
        SpecData.activeAgreement.copy(
          eserviceId = eService.id,
          descriptorId = currentDescriptor.id,
          consumerId = consumer.id
        )

      mockTenantRetrieve(consumer.id, consumer)
      mockAgreementRetrieve(agreement.toPersistent)
      mockEServiceRetrieve(eService.id, eService)

      Get() ~> service.upgradeAgreementById(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if requester is not the Consumer" in {
      val newerDescriptor   = SpecData.publishedDescriptor.copy(version = "10")
      val currentDescriptor = SpecData.deprecatedDescriptor.copy(version = "1")
      val eService          = SpecData.eService.copy(descriptors = Seq(newerDescriptor, currentDescriptor))
      val consumer          = SpecData.tenant
      val agreement         =
        SpecData.activeAgreement.copy(
          eserviceId = eService.id,
          descriptorId = currentDescriptor.id,
          consumerId = consumer.id
        )

      mockTenantRetrieve(requesterOrgId, SpecData.tenant.copy(id = requesterOrgId))
      mockAgreementRetrieve(agreement.toPersistent)

      Get() ~> service.upgradeAgreementById(agreement.id.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

  }
}
