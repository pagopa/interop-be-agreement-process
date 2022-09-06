package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementprocess.api.impl.AgreementFilter
import it.pagopa.interop.catalogmanagement.client.model.{EService, EServiceDescriptor}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

class AgreementFilterSpec extends AnyWordSpecLike with Matchers with ScalaFutures with SpecHelper {

  val agreementId1: UUID = UUID.randomUUID()
  val agreementId2: UUID = UUID.randomUUID()
  val agreementId3: UUID = UUID.randomUUID()
  val agreementId4: UUID = UUID.randomUUID()
  val agreementId5: UUID = UUID.randomUUID()

  val descriptor1_1: EServiceDescriptor = SpecData.descriptor.copy(version = "1")
  val descriptor1_2: EServiceDescriptor = SpecData.descriptor.copy(version = "2")
  val descriptor1_3: EServiceDescriptor = SpecData.descriptor.copy(version = "3")

  val descriptor2_1: EServiceDescriptor = SpecData.descriptor.copy(version = "1")
  val descriptor2_2: EServiceDescriptor = SpecData.descriptor.copy(version = "10")
  val descriptor2_3: EServiceDescriptor = SpecData.descriptor.copy(version = "100")

  val eService1: EService = SpecData.eService.copy(descriptors = Seq(descriptor1_1, descriptor1_2, descriptor1_3))
  val eService2: EService = SpecData.eService.copy(descriptors = Seq(descriptor2_1, descriptor2_2, descriptor2_3))

  val agreements = Seq(
    SpecData.agreement.copy(id = agreementId1, eserviceId = eService1.id, descriptorId = descriptor1_1.id),
    SpecData.agreement.copy(id = agreementId2, eserviceId = eService1.id, descriptorId = descriptor1_2.id),
    SpecData.agreement.copy(id = agreementId3, eserviceId = eService1.id, descriptorId = descriptor1_3.id),
    SpecData.agreement.copy(id = agreementId4, eserviceId = eService2.id, descriptorId = descriptor2_1.id),
    SpecData.agreement.copy(id = agreementId5, eserviceId = eService2.id, descriptorId = descriptor2_3.id)
  )

  "Agreements filter" should {

    "return only the latest version for each agreement when latest filter is TRUE" in {

      val agreementsToFilter = agreements

      mockEServiceRetrieve(eService1.id, eService1)
      mockEServiceRetrieve(eService2.id, eService2)

      val filtered = AgreementFilter.filterAgreementsByLatestVersion(mockCatalogManagementService, agreementsToFilter)

      filtered.futureValue.map(_.id) should contain only (agreementId3, agreementId5)
    }

  }

}
