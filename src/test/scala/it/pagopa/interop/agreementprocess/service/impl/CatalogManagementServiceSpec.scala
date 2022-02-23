package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.CatalogManagementService
import it.pagopa.interop.catalogmanagement.client.model._
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
        technology = EServiceTechnology.REST,
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
            state = EServiceDescriptorState.DEPRECATED,
            dailyCallsMaxNumber = 1000
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "3",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.DEPRECATED,
            dailyCallsMaxNumber = 1000
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "5",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.ARCHIVED,
            dailyCallsMaxNumber = 1000
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "4",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.PUBLISHED,
            dailyCallsMaxNumber = 1000
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
        state = EServiceDescriptorState.PUBLISHED,
        dailyCallsMaxNumber = 1000
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
        technology = EServiceTechnology.REST,
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
        state = EServiceDescriptorState.PUBLISHED,
        dailyCallsMaxNumber = 1000
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
        technology = EServiceTechnology.REST,
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
            state = EServiceDescriptorState.DEPRECATED,
            dailyCallsMaxNumber = 1000
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "3",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.DEPRECATED,
            dailyCallsMaxNumber = 1000
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "5",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.ARCHIVED,
            dailyCallsMaxNumber = 1000
          ),
          EServiceDescriptor(
            id = UUID.randomUUID(),
            version = "4",
            description = Some("test"),
            audience = Seq("do you hear me?"),
            voucherLifespan = 123,
            interface = None,
            docs = Seq.empty,
            state = EServiceDescriptorState.ARCHIVED,
            dailyCallsMaxNumber = 1000
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
        state = EServiceDescriptorState.PUBLISHED,
        dailyCallsMaxNumber = 1000
      )

      val nextDescriptor = CatalogManagementService.getActiveDescriptorOption(eservice, currentDescriptor)

      nextDescriptor.futureValue shouldBe None
    }

  }

}
