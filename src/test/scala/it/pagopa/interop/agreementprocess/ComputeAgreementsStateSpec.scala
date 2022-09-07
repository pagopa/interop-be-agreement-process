package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.commons.jwt.INTERNAL_ROLE
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class ComputeAgreementsStateSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Agreement State Compute" should {
    "succeed and update required agreements" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)
      val consumerId                               = UUID.randomUUID()
      val attributeId                              = UUID.randomUUID()

      val eServiceAttr = SpecData.catalogCertifiedAttribute()
      val eServiceId1  = UUID.randomUUID()
      val eServiceId2  = UUID.randomUUID()
      val eService1    = SpecData.eService.copy(id = eServiceId1, attributes = eServiceAttr)
      val eService2    = SpecData.eService.copy(id = eServiceId2, attributes = eServiceAttr)

      val draftAgreement           = SpecData.draftAgreement.copy(eserviceId = eServiceId1)
      val pendingAgreement         = SpecData.pendingAgreement.copy(eserviceId = eServiceId1)
      val activeAgreement          = SpecData.activeAgreement.copy(eserviceId = eServiceId1)
      val suspendedAgreement       = SpecData.suspendedAgreement.copy(eserviceId = eServiceId2)
      val missingCertAttrAgreement =
        SpecData.missingCertifiedAttributes.copy(eserviceId = eServiceId2, suspendedByPlatform = Some(true))

      val agreements =
        Seq(draftAgreement, pendingAgreement, activeAgreement, suspendedAgreement, missingCertAttrAgreement)

      mockAgreementsRetrieve(agreements)
      mockEServiceRetrieve(eServiceId1, eService1)
      mockEServiceRetrieve(eServiceId2, eService2)
      mockTenantRetrieve(consumerId, SpecData.tenant)

      mockAgreementUpdateIgnoreSeed(draftAgreement.id)
      mockAgreementUpdateIgnoreSeed(pendingAgreement.id)
      mockAgreementUpdateIgnoreSeed(activeAgreement.id)
      mockAgreementUpdateIgnoreSeed(suspendedAgreement.id)

      Get() ~> service.computeAgreementsByAttribute(consumerId.toString, attributeId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

  }
}
