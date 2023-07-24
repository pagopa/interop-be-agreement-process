package it.pagopa.interop.agreementprocess

import cats.syntax.all._
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.agreementprocess.service.AgreementStateByAttributesFSM._
import it.pagopa.interop.catalogmanagement.model.{Automatic, Manual}
import it.pagopa.interop.agreementmanagement.model.agreement.{
  Active,
  Pending,
  MissingCertifiedAttributes,
  Suspended,
  Archived,
  Rejected,
  Draft
}

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
class AgreementStateByAttributesFSMSpec extends AnyWordSpecLike {

  "from DRAFT" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied and Agreement Approval Policy is AUTOMATIC" in {
      val agreement: Agreement                 = SpecData.agreement.copy(state = DRAFT)
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (descriptorVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(agreement.producerId)

      val descriptorAttr =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared).copy(verified = descriptorVerAttr.verified)
      val tenantAttr     = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)
      val descriptor     =
        SpecData.publishedDescriptor.copy(agreementApprovalPolicy = Automatic.some, attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Active
    }

    "go to ACTIVE when Consumer and Producer are the same, even with unmet attributes" in {
      val producerId: UUID = UUID.randomUUID()

      val descriptorCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr     = SpecData.tenantCertifiedAttribute()
      val descriptorDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr     = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr      = SpecData.tenantVerifiedAttribute()
      val descriptorAttr     =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr         = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement =
        SpecData.agreement.copy(state = DRAFT, producerId = producerId, consumerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Active
    }

    "go to PENDING when Certified and Declared attributes are satisfied and Agreement Approval Policy is not AUTOMATIC" in {
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val descriptorAttr                       = descriptorCertAttr.copy(declared = descriptorDeclAttr.declared)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr)

      val descriptor           =
        SpecData.publishedDescriptor.copy(agreementApprovalPolicy = Manual.some, attributes = descriptorAttr)
      val agreement: Agreement = SpecData.agreement.copy(state = DRAFT)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Pending
    }

    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val descriptorAttr                       = descriptorCertAttr.copy(declared = descriptorDeclAttr.declared)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = DRAFT)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe MissingCertifiedAttributes
    }
  }

  "from PENDING" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Active
    }

    "stay in PENDING when Verified attributes are NOT satisfied" in {
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                      = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                        = SpecData.tenantVerifiedAttribute()
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Pending
    }

    "go to DRAFT when Declared attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                       = SpecData.tenantDeclaredAttribute()
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Draft
    }

    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe MissingCertifiedAttributes
    }
  }

  "from ACTIVE" should {
    "go to SUSPENDED when Certified attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ACTIVE, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Suspended
    }

    "go to SUSPENDED when Declared attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val descriptorDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                       = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ACTIVE, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Suspended
    }

    "go to SUSPENDED when Verified attributes are NOT satisfied" in {
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                      = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                        = SpecData.tenantVerifiedAttribute()
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ACTIVE)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Suspended
    }

    "go to ACTIVE when Consumer and Producer are the same, even with unmet attributes" in {
      val producerId: UUID = UUID.randomUUID()

      val descriptorCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr     = SpecData.tenantCertifiedAttribute()
      val descriptorDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr     = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr      = SpecData.tenantVerifiedAttribute()
      val descriptorAttr     =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr         = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement =
        SpecData.agreement.copy(state = ACTIVE, producerId = producerId, consumerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Active
    }
  }

  "from SUSPENDED" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Active
    }

    "stay in SUSPENDED when Certified attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Suspended
    }

    "stay in SUSPENDED when Declared attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val descriptorDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                       = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Suspended
    }

    "stay in SUSPENDED when Verified attributes are NOT satisfied" in {
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                      = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                        = SpecData.tenantVerifiedAttribute()
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Suspended
    }

    "go to ACTIVE when Consumer and Producer are the same, even with unmet attributes" in {
      val producerId: UUID = UUID.randomUUID()

      val descriptorCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr     = SpecData.tenantCertifiedAttribute()
      val descriptorDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr     = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr      = SpecData.tenantVerifiedAttribute()
      val descriptorAttr     =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr         = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement =
        SpecData.agreement.copy(state = SUSPENDED, producerId = producerId, consumerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Active
    }
  }

  "from ARCHIVED" should {
    "stay in ARCHIVED when Certified, Declared and Verified attributes are satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Archived
    }

    "stay in ARCHIVED when Certified attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Archived
    }

    "stay in ARCHIVED when Declared attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val descriptorDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                       = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Archived
    }

    "stay in ARCHIVED when Verified attributes are NOT satisfied" in {
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                      = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                        = SpecData.tenantVerifiedAttribute()
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Archived
    }
  }

  "from MISSING_CERTIFIED_ATTRIBUTES" should {
    "go to DRAFT when Certified attributes are satisfied" in {
      val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val descriptorAttr                       = descriptorCertAttr
      val tenantAttr                           = List(tenantCertAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = MISSING_CERTIFIED_ATTRIBUTES)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Draft
    }

    "stay in MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val producerId: UUID                     = UUID.randomUUID()
      val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
      val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val descriptorAttr                       =
        descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = MISSING_CERTIFIED_ATTRIBUTES, producerId = producerId)
      val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

      nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe MissingCertifiedAttributes
    }

    "from REJECTED" should {
      "stay in REJECTED when Certified, Declared and Verified attributes are satisfied" in {
        val producerId: UUID                     = UUID.randomUUID()
        val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
        val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
        val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
        val descriptorAttr                       =
          descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED, producerId = producerId)
        val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

        nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Rejected
      }

      "stay in REJECTED when Certified attributes are NOT satisfied" in {
        val producerId: UUID                     = UUID.randomUUID()
        val descriptorCertAttr                   = SpecData.catalogCertifiedAttribute()
        val tenantCertAttr                       = SpecData.tenantCertifiedAttribute()
        val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
        val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
        val descriptorAttr                       =
          descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED, producerId = producerId)
        val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

        nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Rejected
      }

      "stay in REJECTED when Declared attributes are NOT satisfied" in {
        val producerId: UUID                     = UUID.randomUUID()
        val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
        val descriptorDeclAttr                   = SpecData.catalogDeclaredAttribute()
        val tenantDeclAttr                       = SpecData.tenantDeclaredAttribute()
        val (eServiceVerAttr, tenantVerAttr)     = SpecData.matchingVerifiedAttributes(verifierId = producerId)
        val descriptorAttr                       =
          descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED, producerId = producerId)
        val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

        nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Rejected
      }

      "stay in REJECTED when Verified attributes are NOT satisfied" in {
        val (descriptorCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
        val (descriptorDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
        val eServiceVerAttr                      = SpecData.catalogVerifiedAttribute()
        val tenantVerAttr                        = SpecData.tenantVerifiedAttribute()
        val descriptorAttr                       =
          descriptorCertAttr.copy(declared = descriptorDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                           = List(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED)
        val descriptor           = SpecData.descriptor.copy(attributes = descriptorAttr)

        nextState(agreement.toPersistent, descriptor, tenantAttr) shouldBe Rejected
      }
    }
  }

}
