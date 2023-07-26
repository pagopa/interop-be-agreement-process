package it.pagopa.interop.agreementprocess.lifecycle

import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import it.pagopa.interop.catalogmanagement.model.CatalogDescriptor
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement

import java.time.OffsetDateTime
import java.util.UUID

class AttributesRulesSpec extends AnyWordSpecLike {

  "Certified attributes check" should {
    "return true if all EService single attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(
        SpecData.tenantCertifiedAttribute(attr1),
        SpecData.tenantCertifiedAttribute(attr2),
        SpecData.tenantCertifiedAttribute()
      )

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe true
    }

    "return true if at least one attribute in every CatalogItem group attribute is satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(
        SpecData.tenantCertifiedAttribute(attr1),
        SpecData.tenantCertifiedAttribute(attr2),
        SpecData.tenantCertifiedAttribute()
      )

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe true
    }

    "return true if CatalogItem single and group attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = List(
        SpecData.tenantCertifiedAttribute(attr1),
        SpecData.tenantCertifiedAttribute(attr2),
        SpecData.tenantCertifiedAttribute()
      )

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe true
    }

    "return false if at least one CatalogItem single attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(SpecData.tenantCertifiedAttribute(attr1), SpecData.tenantCertifiedAttribute())

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }

    "return false if at least one CatalogItem group attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(SpecData.tenantCertifiedAttribute(attr1), SpecData.tenantCertifiedAttribute())

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }

    "return false if an CatalogItem single attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogSingleAttribute(attr1)))

      val tenantAttr = List(SpecData.tenantRevokedCertifiedAttribute(attr1))

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }

    "return false if the CatalogItem group attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogCertifiedAttribute()
        .copy(certified = Seq(SpecData.catalogGroupAttributes(attr1, UUID.randomUUID())))

      val tenantAttr = List(SpecData.tenantRevokedCertifiedAttribute(attr1))

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      certifiedAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }
  }

  "Declared attributes check" should {
    "return true if all CatalogItem single attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(
        SpecData.tenantDeclaredAttribute(attr1),
        SpecData.tenantDeclaredAttribute(attr2),
        SpecData.tenantDeclaredAttribute()
      )

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe true
    }

    "return true if at least one attribute in every CatalogItem group attribute is satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(
        SpecData.tenantDeclaredAttribute(attr1),
        SpecData.tenantDeclaredAttribute(attr2),
        SpecData.tenantDeclaredAttribute()
      )

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe true
    }

    "return true if CatalogItem single and group attributes are satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = List(
        SpecData.tenantDeclaredAttribute(attr1),
        SpecData.tenantDeclaredAttribute(attr2),
        SpecData.tenantDeclaredAttribute()
      )

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe true
    }

    "return false if at least one CatalogItem single attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(SpecData.tenantDeclaredAttribute(attr1), SpecData.tenantDeclaredAttribute())

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }

    "return false if at least one CatalogItem group attribute is not satisfied" in {
      val attr1 = UUID.randomUUID()
      val attr2 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(SpecData.tenantDeclaredAttribute(attr1), SpecData.tenantDeclaredAttribute())

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }

    "return false if an CatalogItem single attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogSingleAttribute(attr1)))

      val tenantAttr = List(SpecData.tenantRevokedDeclaredAttribute(attr1))

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }

    "return false if the CatalogItem group attribute is assigned but revoked" in {
      val attr1 = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogDeclaredAttribute()
        .copy(declared = Seq(SpecData.catalogGroupAttributes(attr1, UUID.randomUUID())))
      val tenantAttr     = List(SpecData.tenantRevokedDeclaredAttribute(attr1))

      val descriptor: CatalogDescriptor = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant    = SpecData.tenant.copy(attributes = tenantAttr)

      declaredAttributesSatisfied(descriptor, consumer.attributes) shouldBe false
    }
  }

  "Verified attributes check" should {
    "return true if all CatalogItem single attributes are satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(attr2, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe true
    }

    "return true if at least one attribute in every CatalogItem group attribute is satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(attr2, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe true
    }

    "return true if CatalogItem single and group attributes are satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogGroupAttributes(attr2, UUID.randomUUID()))
        )

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(attr2, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe true
    }

    "return false if at least one CatalogItem single attribute is not satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if at least one CatalogItem group attribute is not satisfied" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if an CatalogItem single attribute is assigned but not verified" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1)))

      val tenantAttr = List(SpecData.tenantRevokedVerifiedAttribute(attr1, producerId))

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if the CatalogItem group attribute is assigned but not verified" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogGroupAttributes(attr1, UUID.randomUUID())))

      val tenantAttr = List(SpecData.tenantRevokedVerifiedAttribute(attr1, producerId))

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if a single attribute is verified but not by the PersistentAgreement producer" in {
      val producerId  = UUID.randomUUID()
      val attributeId = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attributeId)))

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attributeId, UUID.randomUUID()),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if a group attribute is verified but not by the PersistentAgreement producer" in {
      val producerId  = UUID.randomUUID()
      val attributeId = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogGroupAttributes(attributeId, UUID.randomUUID())))

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attributeId, UUID.randomUUID()),
        SpecData.tenantVerifiedAttribute(verifierId = producerId)
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if at least one single attribute is expired" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified = Seq(SpecData.catalogSingleAttribute(attr1), SpecData.catalogSingleAttribute(attr2)))

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(
          id = attr2,
          verifierId = producerId,
          extensionDate = OffsetDateTime.now().minusDays(3).some
        )
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }

    "return false if at least one group attribute is expired" in {
      val producerId = UUID.randomUUID()
      val attr1      = UUID.randomUUID()
      val attr2      = UUID.randomUUID()

      val descriptorAttr = SpecData
        .catalogVerifiedAttribute()
        .copy(verified =
          Seq(
            SpecData.catalogGroupAttributes(attr1, UUID.randomUUID()),
            SpecData.catalogGroupAttributes(attr2, UUID.randomUUID())
          )
        )

      val tenantAttr = List(
        SpecData.tenantVerifiedAttribute(attr1, producerId),
        SpecData.tenantVerifiedAttribute(
          id = attr2,
          verifierId = producerId,
          extensionDate = OffsetDateTime.now().minusDays(3).some
        )
      )

      val agreement: PersistentAgreement = SpecData.agreement.copy(producerId = producerId)
      val descriptor: CatalogDescriptor  = SpecData.descriptor.copy(attributes = descriptorAttr)
      val consumer: PersistentTenant     = SpecData.tenant.copy(attributes = tenantAttr)

      verifiedAttributesSatisfied(agreement, descriptor, consumer.attributes) shouldBe false
    }
  }
}
