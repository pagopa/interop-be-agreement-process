package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementmanagement.client.model.{AgreementState, UpdateAgreementSeed}
import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState
import it.pagopa.interop.commons.jwt.INTERNAL_ROLE
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class ComputeAgreementsStateSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Agreement State Compute" should {
    "succeed and update agreements whose EServices contain the attribute when Tenant gained the attribute" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)
      val consumerId                               = UUID.randomUUID()

      val (eServiceAttr, tenantAttr) = SpecData.matchingCertifiedAttributes
      val attributeId                = tenantAttr.certified.get.id

      val eServiceId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val eServiceId3 = UUID.randomUUID()
      val eService1   = SpecData.eService.copy(id = eServiceId1, attributes = eServiceAttr)
      val eService2   = SpecData.eService.copy(id = eServiceId2)
      val eService3   = SpecData.eService.copy(id = eServiceId3, attributes = eServiceAttr)
      val tenant      = SpecData.tenant.copy(attributes = Seq(tenantAttr))

      val suspendedAgreement1      =
        SpecData.suspendedAgreement.copy(
          eserviceId = eServiceId1,
          suspendedByProducer = None,
          suspendedByConsumer = None,
          suspendedByPlatform = Some(true)
        )
      val missingCertAttrAgreement =
        SpecData.missingCertifiedAttributesAgreement.copy(eserviceId = eServiceId1, suspendedByPlatform = Some(true))
      val suspendedAgreement2      =
        SpecData.suspendedAgreement.copy(eserviceId = eServiceId2, suspendedByProducer = Some(true))
      val suspendedAgreement3      =
        SpecData.suspendedAgreement.copy(
          eserviceId = eServiceId3,
          suspendedByProducer = Some(true),
          suspendedByConsumer = None,
          suspendedByPlatform = Some(true)
        )
      val agreements               =
        Seq(suspendedAgreement1, missingCertAttrAgreement, suspendedAgreement2, suspendedAgreement3)

      val expectedSeed1 = UpdateAgreementSeed(
        state = AgreementState.ACTIVE,
        certifiedAttributes = suspendedAgreement1.certifiedAttributes,
        declaredAttributes = suspendedAgreement1.declaredAttributes,
        verifiedAttributes = suspendedAgreement1.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        stamps = suspendedAgreement1.stamps,
        consumerNotes = suspendedAgreement1.consumerNotes,
        rejectionReason = suspendedAgreement1.rejectionReason
      )

      val expectedSeed2 = UpdateAgreementSeed(
        state = AgreementState.DRAFT,
        certifiedAttributes = missingCertAttrAgreement.certifiedAttributes,
        declaredAttributes = missingCertAttrAgreement.declaredAttributes,
        verifiedAttributes = missingCertAttrAgreement.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        suspendedByPlatform = Some(false),
        stamps = missingCertAttrAgreement.stamps,
        consumerNotes = missingCertAttrAgreement.consumerNotes,
        rejectionReason = missingCertAttrAgreement.rejectionReason
      )

      val expectedSeed3 = UpdateAgreementSeed(
        state = AgreementState.SUSPENDED,
        certifiedAttributes = suspendedAgreement3.certifiedAttributes,
        declaredAttributes = suspendedAgreement3.declaredAttributes,
        verifiedAttributes = suspendedAgreement3.verifiedAttributes,
        suspendedByConsumer = None,
        suspendedByProducer = Some(true),
        suspendedByPlatform = Some(false),
        stamps = suspendedAgreement3.stamps,
        consumerNotes = suspendedAgreement3.consumerNotes,
        rejectionReason = suspendedAgreement3.rejectionReason
      )

      mockAgreementsRetrieve(agreements)
      mockEServiceRetrieve(eServiceId1, eService1)
      mockEServiceRetrieve(eServiceId2, eService2)
      mockEServiceRetrieve(eServiceId3, eService3)
      mockTenantRetrieve(consumerId, tenant)

      mockAgreementUpdate(suspendedAgreement1.id, expectedSeed1, suspendedAgreement1)
      mockAgreementUpdate(missingCertAttrAgreement.id, expectedSeed2, missingCertAttrAgreement)
      mockAgreementUpdate(suspendedAgreement3.id, expectedSeed3, suspendedAgreement3)

      mockClientStateUpdate(
        suspendedAgreement1.eserviceId,
        suspendedAgreement1.consumerId,
        suspendedAgreement1.id,
        ClientComponentState.ACTIVE
      )

      Get() ~> service.computeAgreementsByAttribute(consumerId.toString, attributeId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed and update agreements whose EServices contain the attribute when Tenant lost the attribute" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)
      val consumerId                               = UUID.randomUUID()
      val attributeId                              = UUID.randomUUID()

      val eServiceAttr = SpecData.catalogCertifiedAttribute(attributeId)
      val eServiceId1  = UUID.randomUUID()
      val eServiceId2  = UUID.randomUUID()
      val eService1    = SpecData.eService.copy(id = eServiceId1, attributes = eServiceAttr)
      val eService2    = SpecData.eService.copy(id = eServiceId2)
      val tenant       = SpecData.tenant

      val draftAgreement   = SpecData.draftAgreement.copy(eserviceId = eServiceId1)
      val pendingAgreement = SpecData.pendingAgreement.copy(eserviceId = eServiceId1)
      val activeAgreement  = SpecData.activeAgreement.copy(eserviceId = eServiceId1)
      val activeAgreement2 = SpecData.activeAgreement.copy(eserviceId = eServiceId2)

      val agreements =
        Seq(draftAgreement, pendingAgreement, activeAgreement, activeAgreement2)

      mockAgreementsRetrieve(agreements)
      mockEServiceRetrieve(eServiceId1, eService1)
      mockEServiceRetrieve(eServiceId2, eService2)
      mockTenantRetrieve(consumerId, tenant)

      mockAgreementUpdateIgnoreSeed(draftAgreement.id)
      mockAgreementUpdateIgnoreSeed(pendingAgreement.id)
      mockAgreementUpdateIgnoreSeed(activeAgreement.id)

      mockClientStateUpdate(
        activeAgreement.eserviceId,
        activeAgreement.consumerId,
        activeAgreement.id,
        ClientComponentState.INACTIVE
      )

      Get() ~> service.computeAgreementsByAttribute(consumerId.toString, attributeId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed and do not update agreements if status has not changed" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)
      val consumerId                               = UUID.randomUUID()
      val attributeId                              = UUID.randomUUID()

      val eServiceAttr = SpecData.catalogCertifiedAttribute()
      val eServiceId1  = UUID.randomUUID()
      val eServiceId2  = UUID.randomUUID()
      val eService1    = SpecData.eService.copy(id = eServiceId1)
      val eService2    = SpecData.eService.copy(id = eServiceId2, attributes = eServiceAttr)

      val agreementWithoutChanges1  = SpecData.draftAgreement.copy(eserviceId = eServiceId1)
      val agreementWithoutChanges2  = SpecData.pendingAgreement.copy(eserviceId = eServiceId1)
      val agreementAlreadySuspended =
        SpecData.suspendedAgreement.copy(eserviceId = eServiceId2, suspendedByPlatform = Some(true))

      val agreements = Seq(agreementWithoutChanges1, agreementWithoutChanges2, agreementAlreadySuspended)

      mockAgreementsRetrieve(agreements)
      mockEServiceRetrieve(eServiceId1, eService1)
      mockEServiceRetrieve(eServiceId2, eService2)
      mockTenantRetrieve(consumerId, SpecData.tenant)

      Get() ~> service.computeAgreementsByAttribute(consumerId.toString, attributeId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

  }
}
