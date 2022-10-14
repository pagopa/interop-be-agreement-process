package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.agreementprocess.service.AgreementStateByAttributesFSM._
import it.pagopa.interop.catalogmanagement.client.model.AgreementApprovalPolicy.{AUTOMATIC, MANUAL}
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementStateByAttributesFSMSpec extends AnyWordSpecLike {

  "from DRAFT" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied and Agreement Approval Policy is AUTOMATIC" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val descriptor           = SpecData.publishedDescriptor.copy(agreementApprovalPolicy = AUTOMATIC)
      val agreement: Agreement = SpecData.agreement.copy(state = DRAFT)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr, descriptors = Seq(descriptor))
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe PENDING
    }

    "go to ACTIVE when Consumer and Producer are the same, even with unmet attributes" in {
      val producerId: UUID = UUID.randomUUID()

      val eServiceCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr   = SpecData.tenantCertifiedAttribute()
      val eServiceDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr   = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr  = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr    = SpecData.tenantVerifiedAttribute()
      val eServiceAttr     =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr       = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement =
        SpecData.agreement.copy(state = DRAFT, producerId = producerId, consumerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr, producerId = producerId)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr, id = producerId)

      nextState(agreement, eService, consumer) shouldBe ACTIVE
    }

    "go to PENDING when Certified and Declared attributes are satisfied and Agreement Approval Policy is not AUTOMATIC" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val descriptor           = SpecData.publishedDescriptor.copy(agreementApprovalPolicy = MANUAL)
      val agreement: Agreement = SpecData.agreement.copy(state = DRAFT)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr, descriptors = Seq(descriptor))
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe PENDING
    }

    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = DRAFT)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe MISSING_CERTIFIED_ATTRIBUTES
    }
  }

  "from PENDING" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe ACTIVE
    }

    "stay in PENDING when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe PENDING
    }

    "go to DRAFT when Declared attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe DRAFT
    }

    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = PENDING, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe MISSING_CERTIFIED_ATTRIBUTES
    }
  }

  "from ACTIVE" should {
    "go to SUSPENDED when Certified attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ACTIVE, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe SUSPENDED
    }

    "go to SUSPENDED when Declared attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ACTIVE, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe SUSPENDED
    }

    "go to SUSPENDED when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ACTIVE)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe SUSPENDED
    }

    "go to ACTIVE when Consumer and Producer are the same, even with unmet attributes" in {
      val producerId: UUID = UUID.randomUUID()

      val eServiceCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr   = SpecData.tenantCertifiedAttribute()
      val eServiceDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr   = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr  = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr    = SpecData.tenantVerifiedAttribute()
      val eServiceAttr     =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr       = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement =
        SpecData.agreement.copy(state = ACTIVE, producerId = producerId, consumerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr, producerId = producerId)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr, id = producerId)

      nextState(agreement, eService, consumer) shouldBe ACTIVE
    }
  }

  "from SUSPENDED" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe ACTIVE
    }

    "stay in SUSPENDED when Certified attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe SUSPENDED
    }

    "stay in SUSPENDED when Declared attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe SUSPENDED
    }

    "stay in SUSPENDED when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = SUSPENDED)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe SUSPENDED
    }

    "go to ACTIVE when Consumer and Producer are the same, even with unmet attributes" in {
      val producerId: UUID = UUID.randomUUID()

      val eServiceCertAttr = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr   = SpecData.tenantCertifiedAttribute()
      val eServiceDeclAttr = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr   = SpecData.tenantDeclaredAttribute()
      val eServiceVerAttr  = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr    = SpecData.tenantVerifiedAttribute()
      val eServiceAttr     =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr       = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement =
        SpecData.agreement.copy(state = SUSPENDED, producerId = producerId, consumerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr, producerId = producerId)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr, id = producerId)

      nextState(agreement, eService, consumer) shouldBe ACTIVE
    }
  }

  "from ARCHIVED" should {
    "stay in ARCHIVED when Certified, Declared and Verified attributes are satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe ARCHIVED
    }

    "stay in ARCHIVED when Certified attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe ARCHIVED
    }

    "stay in ARCHIVED when Declared attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe ARCHIVED
    }

    "stay in ARCHIVED when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = ARCHIVED)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe ARCHIVED
    }
  }

  "from MISSING_CERTIFIED_ATTRIBUTES" should {
    "go to DRAFT when Certified attributes are satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceAttr                       = eServiceCertAttr
      val tenantAttr                         = Seq(tenantCertAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = MISSING_CERTIFIED_ATTRIBUTES)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe DRAFT
    }

    "stay in MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val producerId: UUID                   = UUID.randomUUID()
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val agreement: Agreement = SpecData.agreement.copy(state = MISSING_CERTIFIED_ATTRIBUTES, producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(agreement, eService, consumer) shouldBe MISSING_CERTIFIED_ATTRIBUTES
    }

    "from REJECTED" should {
      "stay in REJECTED when Certified, Declared and Verified attributes are satisfied" in {
        val producerId: UUID                   = UUID.randomUUID()
        val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
        val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
        val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
        val eServiceAttr                       =
          eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED, producerId = producerId)
        val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
        val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

        nextState(agreement, eService, consumer) shouldBe REJECTED
      }

      "stay in REJECTED when Certified attributes are NOT satisfied" in {
        val producerId: UUID                   = UUID.randomUUID()
        val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
        val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
        val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
        val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
        val eServiceAttr                       =
          eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED, producerId = producerId)
        val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
        val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

        nextState(agreement, eService, consumer) shouldBe REJECTED
      }

      "stay in REJECTED when Declared attributes are NOT satisfied" in {
        val producerId: UUID                   = UUID.randomUUID()
        val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
        val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
        val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
        val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes(verifierId = producerId)
        val eServiceAttr                       =
          eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED, producerId = producerId)
        val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
        val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

        nextState(agreement, eService, consumer) shouldBe REJECTED
      }

      "stay in REJECTED when Verified attributes are NOT satisfied" in {
        val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
        val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
        val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
        val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
        val eServiceAttr                       =
          eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
        val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

        val agreement: Agreement = SpecData.agreement.copy(state = REJECTED)
        val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
        val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

        nextState(agreement, eService, consumer) shouldBe REJECTED
      }
    }
  }

  "Certified attributes check" should {
    "return true if all EService single attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(
        SpecData.tenantCertifiedAttribute(attr1),
        SpecData.tenantCertifiedAttribute(attr2),
        SpecData.tenantCertifiedAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return true if at least one attribute in every EService group attribute is satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(
        SpecData.tenantCertifiedAttribute(attr1),
        SpecData.tenantCertifiedAttribute(attr2),
        SpecData.tenantCertifiedAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return true if EService single and group attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = Seq(
        SpecData.tenantCertifiedAttribute(attr1),
        SpecData.tenantCertifiedAttribute(attr2),
        SpecData.tenantCertifiedAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return false if at least one EService single attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(SpecData.tenantCertifiedAttribute(attr1), SpecData.tenantCertifiedAttribute())

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if at least one EService group attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(SpecData.tenantCertifiedAttribute(attr1), SpecData.tenantCertifiedAttribute())

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if an EService single attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogSingleAttribute(attr1)))

      val tenantAttr = Seq(SpecData.tenantRevokedCertifiedAttribute(attr1))

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if the EService group attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogGroupAttributes(attr1, UUID.randomUUID())))

      val tenantAttr = Seq(SpecData.tenantRevokedCertifiedAttribute(attr1))

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(eService, consumer) shouldBe false
    }
  }

  "Declared attributes check" should {
    "return true if all EService single attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(
        SpecData.tenantDeclaredAttribute(attr1),
        SpecData.tenantDeclaredAttribute(attr2),
        SpecData.tenantDeclaredAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return true if at least one attribute in every EService group attribute is satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(
        SpecData.tenantDeclaredAttribute(attr1),
        SpecData.tenantDeclaredAttribute(attr2),
        SpecData.tenantDeclaredAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return true if EService single and group attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = Seq(
        SpecData.tenantDeclaredAttribute(attr1),
        SpecData.tenantDeclaredAttribute(attr2),
        SpecData.tenantDeclaredAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return false if at least one EService single attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(SpecData.tenantDeclaredAttribute(attr1), SpecData.tenantDeclaredAttribute())

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if at least one EService group attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(SpecData.tenantDeclaredAttribute(attr1), SpecData.tenantDeclaredAttribute())

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if an EService single attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogSingleAttribute(attr1)))

      val tenantAttr = Seq(SpecData.tenantRevokedDeclaredAttribute(attr1))

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if the EService group attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogGroupAttributes(attr1, UUID.randomUUID())))
      val tenantAttr   = Seq(SpecData.tenantRevokedDeclaredAttribute(attr1))

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(eService, consumer) shouldBe false
    }
  }

  "Verified attributes check" should {
    "return true if all EService single attributes are satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(attr2, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe true
    }

    "return true if at least one attribute in every EService group attribute is satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(attr2, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe true
    }

    "return true if EService single and group attributes are satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(attr2, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe true
    }

    "return false if at least one EService single attribute is not satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe false
    }

    "return false if at least one EService group attribute is not satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe false
    }

    "return false if an EService single attribute is assigned but not verified" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1)))

      val tenantAttr = Seq(SpecData.tenantRevokedVerifiedAttribute(attr1, producerId))

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe false
    }

    "return false if the EService group attribute is assigned but not verified" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogGroupAttributes(attr1, UUID.randomUUID())))

      val tenantAttr = Seq(SpecData.tenantRevokedVerifiedAttribute(attr1, producerId))

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe false
    }

    "return false if a single attribute is verified but not by the Agreement producer" in {
      val producerId  = UUID.randomUUID()
      val attributeId = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attributeId)))

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attributeId, UUID.randomUUID()),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe false
    }

    "return false if a group attribute is verified but not by the Agreement producer" in {
      val producerId  = UUID.randomUUID()
      val attributeId = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogGroupAttributes(attributeId, UUID.randomUUID())))

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attributeId, UUID.randomUUID()),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: Agreement = SpecData.agreement.copy(producerId = producerId)
      val eService: EService   = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, eService, consumer) shouldBe false
    }
  }
}
