package it.pagopa.interop.agreementprocess.lifecycle

import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AttributesRulesSpec extends AnyWordSpecLike {

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
