package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.client.model.{AgreementState, UpdateAgreementSeed}
import it.pagopa.interop.agreementprocess.model.ComputeAgreementStatePayload
import it.pagopa.interop.authorizationmanagement.client.model.ClientComponentState
import it.pagopa.interop.commons.jwt.INTERNAL_ROLE
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class ComputeAgreementsStateSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Agreement State Compute" should {
    "succeed and update agreements whose EServices contain the attribute when Tenant gained the attribute" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)

      val (descriptorAttr, tenantAttr) = SpecData.compactMatchingCertifiedAttributes
      val attributeId                  = tenantAttr.certified.map(_.id).get

      val descriptor1 = SpecData.descriptor.copy(attributes = descriptorAttr)
      val descriptor2 = SpecData.descriptor
      val descriptor3 = SpecData.descriptor.copy(attributes = descriptorAttr)
      val eService1   = SpecData.eService.copy(id = UUID.randomUUID(), descriptors = descriptor1 :: Nil)
      val eService2   = SpecData.eService.copy(id = UUID.randomUUID(), descriptors = descriptor2 :: Nil)
      val eService3   = SpecData.eService.copy(id = UUID.randomUUID(), descriptors = descriptor3 :: Nil)
      val tenant      = SpecData.compactTenant.copy(attributes = List(tenantAttr))
      val payload     = ComputeAgreementStatePayload(attributeId, tenant)

      val suspendedAgreement1      =
        SpecData.suspendedByPlatformAgreement.copy(
          eserviceId = eService1.id,
          descriptorId = descriptor1.id,
          suspendedByProducer = None,
          suspendedByConsumer = None,
          suspendedByPlatform = Some(true)
        )
      val missingCertAttrAgreement =
        SpecData.missingCertifiedAttributesAgreement.copy(
          eserviceId = eService1.id,
          descriptorId = descriptor1.id,
          suspendedByPlatform = Some(true)
        )
      val suspendedAgreement2      =
        SpecData.suspendedByProducerAgreement.copy(
          eserviceId = eService2.id,
          descriptorId = descriptor2.id,
          suspendedByProducer = Some(true)
        )
      val suspendedAgreement3      =
        SpecData.suspendedByProducerAgreement.copy(
          eserviceId = eService3.id,
          descriptorId = descriptor3.id,
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

      mockAgreementsRetrieve(agreements.map(_.toPersistent))
      mockEServiceRetrieve(eService1.id, eService1)
      mockEServiceRetrieve(eService2.id, eService2)
      mockEServiceRetrieve(eService3.id, eService3)

      mockAgreementUpdate(suspendedAgreement1.id, expectedSeed1, suspendedAgreement1)
      mockAgreementUpdate(missingCertAttrAgreement.id, expectedSeed2, missingCertAttrAgreement)
      mockAgreementUpdate(suspendedAgreement3.id, expectedSeed3, suspendedAgreement3)

      mockClientStateUpdate(
        suspendedAgreement1.eserviceId,
        suspendedAgreement1.consumerId,
        suspendedAgreement1.id,
        ClientComponentState.ACTIVE
      )

      Get() ~> service.computeAgreementsByAttribute(payload) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed and update agreements whose EServices contain the attribute when Tenant lost the attribute" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)
      val attributeId                              = UUID.randomUUID()

      val descriptorAttr = SpecData.catalogCertifiedAttribute(attributeId)
      val descriptor1    = SpecData.descriptor.copy(id = UUID.randomUUID(), attributes = descriptorAttr)
      val descriptor2    = SpecData.descriptor.copy(id = UUID.randomUUID())
      val eService1      = SpecData.eService.copy(id = UUID.randomUUID(), descriptors = descriptor1 :: Nil)
      val eService2      = SpecData.eService.copy(id = UUID.randomUUID(), descriptors = descriptor2 :: Nil)
      val tenant         = SpecData.compactTenant
      val payload        = ComputeAgreementStatePayload(attributeId, tenant)

      val draftAgreement   = SpecData.draftAgreement.copy(eserviceId = eService1.id, descriptorId = descriptor1.id)
      val pendingAgreement = SpecData.pendingAgreement.copy(eserviceId = eService1.id, descriptorId = descriptor1.id)
      val activeAgreement  = SpecData.activeAgreement.copy(eserviceId = eService1.id, descriptorId = descriptor1.id)
      val activeAgreement2 = SpecData.activeAgreement.copy(eserviceId = eService2.id, descriptorId = descriptor2.id)

      val agreements =
        Seq(draftAgreement, pendingAgreement, activeAgreement, activeAgreement2)

      mockAgreementsRetrieve(agreements.map(_.toPersistent))
      mockEServiceRetrieve(eService1.id, eService1)
      mockEServiceRetrieve(eService2.id, eService2)

      mockAgreementUpdateIgnoreSeed(draftAgreement.id)
      mockAgreementUpdateIgnoreSeed(pendingAgreement.id)
      mockAgreementUpdateIgnoreSeed(activeAgreement.id)

      mockClientStateUpdate(
        activeAgreement.eserviceId,
        activeAgreement.consumerId,
        activeAgreement.id,
        ClientComponentState.INACTIVE
      )

      Get() ~> service.computeAgreementsByAttribute(payload) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed and do not update agreements if status has not changed" in {
      implicit val contexts: Seq[(String, String)] = contextWithRole(INTERNAL_ROLE)

      val descriptorAttr = SpecData.catalogCertifiedAttribute()
      val eServiceId1    = UUID.randomUUID()
      val eServiceId2    = UUID.randomUUID()
      val descriptor2    = SpecData.descriptor.copy(attributes = descriptorAttr)
      val eService1      = SpecData.eService.copy(id = eServiceId1)
      val eService2      = SpecData.eService.copy(id = eServiceId2, descriptors = descriptor2 :: Nil)

      val agreementWithoutChanges1  = SpecData.draftAgreement.copy(eserviceId = eServiceId1)
      val agreementWithoutChanges2  = SpecData.pendingAgreement.copy(eserviceId = eServiceId1)
      val agreementAlreadySuspended =
        SpecData.suspendedByPlatformAgreement.copy(eserviceId = eServiceId2, suspendedByPlatform = Some(true))

      val agreements = Seq(agreementWithoutChanges1, agreementWithoutChanges2, agreementAlreadySuspended)

      mockAgreementsRetrieve(agreements.map(_.toPersistent))
      mockEServiceRetrieve(eServiceId1, eService1)
      mockEServiceRetrieve(eServiceId2, eService2)

      Get() ~> service.computeAgreementsByAttribute(SpecData.computeAgreementStatePayload) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

  }
}
