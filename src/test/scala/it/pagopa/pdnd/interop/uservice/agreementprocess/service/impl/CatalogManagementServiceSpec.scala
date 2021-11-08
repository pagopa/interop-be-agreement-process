package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.CatalogManagementService
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  ARCHIVED,
  Attributes,
  DEPRECATED,
  EService,
  EServiceDescriptor,
  PUBLISHED,
  REST
}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class CatalogManagementServiceSpec extends AnyWordSpecLike with Matchers with ScalaFutures {

  "CatalogManagementService" should {

    "return the next published version" in {

      val eservice: EService = EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "name",
        description = "description",
        technology = REST,
        attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty),
        descriptors = Seq(
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "20",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = DEPRECATED
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "3",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = DEPRECATED
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "5",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = ARCHIVED
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "4",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = PUBLISHED
          )
        )
      )
      val currentDescriptor: EServiceDescriptor = EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "2",
        description = Some("test"),
        audience = Seq("do you hear me?"),
        voucherLifespan = 123,
        interface = None,
        docs = Seq.empty,
        status = PUBLISHED
      )

      val nextDescriptor = CatalogManagementService.getActiveDescriptorOption(eservice, currentDescriptor)

      nextDescriptor.futureValue.get.version shouldBe "4"
    }

    "return NO next published version when no e-service descriptor exist" in {

      val eservice: EService = EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "name",
        description = "description",
        technology = REST,
        attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty),
        descriptors = Seq.empty
      )

      val currentDescriptor: EServiceDescriptor = EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "2",
        description = Some("test"),
        audience = Seq("do you hear me?"),
        voucherLifespan = 123,
        interface = None,
        docs = Seq.empty,
        status = PUBLISHED
      )

      val nextDescriptor = CatalogManagementService.getActiveDescriptorOption(eservice, currentDescriptor)

      nextDescriptor.futureValue shouldBe None
    }

    "return NO next published version when no published next descriptors exist" in {

      val eservice: EService = EService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "name",
        description = "description",
        technology = REST,
        attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty),
        descriptors = Seq(
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "20",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = DEPRECATED
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "3",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = DEPRECATED
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "5",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = ARCHIVED
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "4",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            status = ARCHIVED
          )
        )
      )
      val currentDescriptor: EServiceDescriptor = EServiceDescriptor(
        id = UUID.randomUUID(),
        version = "2",
        description = Some("test"),
        audience = Seq("do you hear me?"),
        voucherLifespan = 123,
        interface = None,
        docs = Seq.empty,
        status = PUBLISHED
      )

      val nextDescriptor = CatalogManagementService.getActiveDescriptorOption(eservice, currentDescriptor)

      nextDescriptor.futureValue shouldBe None
    }

  }

}
