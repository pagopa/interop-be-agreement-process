package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model.AgreementState._
import it.pagopa.interop.agreementprocess.service.AgreementStateByAttributesFSM._
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementStateByAttributesFSMSpec extends AnyWordSpecLike {

  "from DRAFT" should {
    "go to PENDING when Certified and Declared attributes are satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(DRAFT, eService, consumer) shouldBe PENDING
    }

    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceAttr                       = eServiceCertAttr.copy(declared = eServiceDeclAttr.declared)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(DRAFT, eService, consumer) shouldBe MISSING_CERTIFIED_ATTRIBUTES
    }
  }

  "from PENDING" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(PENDING, eService, consumer) shouldBe ACTIVE
    }

    "go to PENDING when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(PENDING, eService, consumer) shouldBe PENDING
    }

    "go to DRAFT when Declared attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(PENDING, eService, consumer) shouldBe DRAFT
    }

    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(PENDING, eService, consumer) shouldBe MISSING_CERTIFIED_ATTRIBUTES
    }
  }

  "from ACTIVE" should {
    "go to SUSPENDED when Certified attributes are NOT satisfied" in {
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(ACTIVE, eService, consumer) shouldBe SUSPENDED
    }

    "go to SUSPENDED when Declared attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(ACTIVE, eService, consumer) shouldBe SUSPENDED
    }

    "go to SUSPENDED when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(ACTIVE, eService, consumer) shouldBe SUSPENDED
    }
  }

  "from SUSPENDED" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(SUSPENDED, eService, consumer) shouldBe ACTIVE
    }

    "go to SUSPENDED when Certified attributes are NOT satisfied" in {
      val eServiceCertAttr                   = SpecData.catalogCertifiedAttribute()
      val tenantCertAttr                     = SpecData.tenantCertifiedAttribute()
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(SUSPENDED, eService, consumer) shouldBe SUSPENDED
    }

    "go to SUSPENDED when Declared attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val eServiceDeclAttr                   = SpecData.catalogDeclaredAttribute()
      val tenantDeclAttr                     = SpecData.tenantDeclaredAttribute()
      val (eServiceVerAttr, tenantVerAttr)   = SpecData.matchingVerifiedAttributes
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(SUSPENDED, eService, consumer) shouldBe SUSPENDED
    }

    "go to SUSPENDED when Verified attributes are NOT satisfied" in {
      val (eServiceCertAttr, tenantCertAttr) = SpecData.matchingCertifiedAttributes
      val (eServiceDeclAttr, tenantDeclAttr) = SpecData.matchingDeclaredAttributes
      val eServiceVerAttr                    = SpecData.catalogVerifiedAttribute()
      val tenantVerAttr                      = SpecData.tenantVerifiedAttribute()
      val eServiceAttr                       =
        eServiceCertAttr.copy(declared = eServiceDeclAttr.declared, verified = eServiceVerAttr.verified)
      val tenantAttr                         = Seq(tenantCertAttr, tenantDeclAttr, tenantVerAttr)

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      nextState(SUSPENDED, eService, consumer) shouldBe SUSPENDED
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
  }

  "Verified attributes check" should {
    "return true if all EService single attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1),
        SpecData.tenantVerifiedAttribute(attr2),
        SpecData.tenantVerifiedAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return true if at least one attribute in every EService group attribute is satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1),
        SpecData.tenantVerifiedAttribute(attr2),
        SpecData.tenantVerifiedAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return true if EService single and group attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = Seq(
        SpecData.tenantVerifiedAttribute(attr1),
        SpecData.tenantVerifiedAttribute(attr2),
        SpecData.tenantVerifiedAttribute()
      )

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(eService, consumer) shouldBe true
    }

    "return false if at least one EService single attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = Seq(SpecData.tenantVerifiedAttribute(attr1), SpecData.tenantVerifiedAttribute())

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(eService, consumer) shouldBe false
    }

    "return false if at least one EService group attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val eServiceAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = Seq(SpecData.tenantVerifiedAttribute(attr1), SpecData.tenantVerifiedAttribute())

      val eService: EService = SpecData.eService.copy(attributes = eServiceAttr)
      val consumer: Tenant   = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(eService, consumer) shouldBe false
    }
  }
}
