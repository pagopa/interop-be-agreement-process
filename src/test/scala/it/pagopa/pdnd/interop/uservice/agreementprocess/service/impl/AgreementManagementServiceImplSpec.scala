package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{VerifiedAttribute, VerifiedAttributeSeed}
import it.pagopa.pdnd.interop.uservice.agreementprocess.SpecHelper
import it.pagopa.pdnd.interop.uservice.agreementprocess.server.impl.AgreementManagementAPI
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.AgreementManagementService
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, AttributeValue, Attributes}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementManagementServiceImplSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with SpecHelper
    with AgreementManagementAPI {

  implicit val testSystem = system.classicSystem

  val attribute1 = UUID.randomUUID()
  val attribute2 = UUID.randomUUID()
  val attribute3 = UUID.randomUUID()
  val attribute4 = UUID.randomUUID()
  val attribute5 = UUID.randomUUID()
  val attribute6 = UUID.randomUUID()
  val attribute7 = UUID.randomUUID()

  val agreementManagementServiceImpl: AgreementManagementService = agreementManagement()

  "attribute verification" should {

    "verify agreements without attributes should return valid state" in {

      val consumerAttributesIds: Seq[String] =
        Seq(attribute1, attribute2, attribute3, attribute4, attribute5, attribute7).map(_.toString)

      val eserviceAttributes: Attributes =
        Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty)
      val agreementVerifiedAttributes: Seq[VerifiedAttribute] = Seq.empty

      val f =
        agreementManagementServiceImpl
          .verifyAttributes(consumerAttributesIds, eserviceAttributes, agreementVerifiedAttributes);

      f.futureValue shouldBe true
    }

    "verify attributes validity properly" in {

      val consumerAttributesIds: Seq[String] =
        Seq(attribute1, attribute2, attribute3, attribute4, attribute5, attribute7).map(_.toString)

      val eserviceAttributes: Attributes =
        Attributes(
          certified = Seq(
            Attribute(single = Some(AttributeValue(attribute1.toString, false))),
            Attribute(single = Some(AttributeValue(attribute2.toString, false))),
            Attribute(group =
              Some(
                Seq(
                  AttributeValue(attribute5.toString, false),
                  AttributeValue(attribute6.toString, false),
                  AttributeValue(attribute7.toString, false)
                )
              )
            )
          ),
          declared = Seq.empty,
          verified = Seq.empty
        )
      val agreementVerifiedAttributes: Seq[VerifiedAttribute] =
        Seq(VerifiedAttribute(id = attribute1, verified = true), VerifiedAttribute(id = attribute2, verified = true))

      val f =
        agreementManagementServiceImpl
          .verifyAttributes(consumerAttributesIds, eserviceAttributes, agreementVerifiedAttributes);

      f.futureValue shouldBe true
    }

    "verify parties without attributes should return an invalid state" in {
      val consumerAttributesIds: Seq[String] = Seq.empty

      val eserviceAttributes: Attributes =
        Attributes(
          certified = Seq(Attribute(single = Some(AttributeValue(attribute5.toString, false)))),
          declared = Seq.empty,
          verified = Seq.empty
        )
      val agreementVerifiedAttributes: Seq[VerifiedAttribute] =
        Seq(VerifiedAttribute(id = attribute1, verified = true), VerifiedAttribute(id = attribute2, verified = true))

      val f =
        agreementManagementServiceImpl.verifyAttributes(
          consumerAttributesIds,
          eserviceAttributes,
          agreementVerifiedAttributes
        );

      f.failed.futureValue shouldBe a[RuntimeException]
    }

    "verify not valid simple certified attributes should return a failed future" in {

      val consumerAttributesIds: Seq[String] = Seq(attribute1, attribute2, attribute3, attribute4).map(_.toString)

      val eserviceAttributes: Attributes =
        Attributes(
          certified = Seq(Attribute(single = Some(AttributeValue(attribute5.toString, false)))),
          declared = Seq.empty,
          verified = Seq.empty
        )
      val agreementVerifiedAttributes: Seq[VerifiedAttribute] =
        Seq(VerifiedAttribute(id = attribute1, verified = true), VerifiedAttribute(id = attribute2, verified = true))

      val f =
        agreementManagementServiceImpl.verifyAttributes(
          consumerAttributesIds,
          eserviceAttributes,
          agreementVerifiedAttributes
        );

      f.failed.futureValue shouldBe a[RuntimeException]
    }

    "verify not valid group certified attributes should return a failed future" in {

      val consumerAttributesIds: Seq[String] = Seq(attribute1, attribute2, attribute3, attribute4).map(_.toString)

      val eserviceAttributes: Attributes =
        Attributes(
          certified = Seq(
            Attribute(group =
              Some(
                Seq(
                  AttributeValue(attribute5.toString, false),
                  AttributeValue(attribute6.toString, false),
                  AttributeValue(attribute7.toString, false)
                )
              )
            )
          ),
          declared = Seq.empty,
          verified = Seq.empty
        )
      val agreementVerifiedAttributes: Seq[VerifiedAttribute] =
        Seq(VerifiedAttribute(id = attribute1, verified = true), VerifiedAttribute(id = attribute2, verified = true))

      val f =
        agreementManagementServiceImpl.verifyAttributes(
          consumerAttributesIds,
          eserviceAttributes,
          agreementVerifiedAttributes
        );

      f.failed.futureValue shouldBe a[RuntimeException]
    }

    "verify that not verified attributes should break the validation of the agreement" in {

      val consumerAttributesIds: Seq[String] =
        Seq(attribute1, attribute2, attribute3, attribute4, attribute5, attribute7).map(_.toString)

      val eserviceAttributes: Attributes =
        Attributes(
          certified = Seq(
            Attribute(single = Some(AttributeValue(attribute1.toString, false))),
            Attribute(single = Some(AttributeValue(attribute2.toString, false))),
            Attribute(group =
              Some(
                Seq(
                  AttributeValue(attribute5.toString, false),
                  AttributeValue(attribute6.toString, false),
                  AttributeValue(attribute7.toString, false)
                )
              )
            )
          ),
          declared = Seq.empty,
          verified = Seq.empty
        )
      val agreementVerifiedAttributes: Seq[VerifiedAttribute] =
        Seq(VerifiedAttribute(id = attribute1, verified = false), VerifiedAttribute(id = attribute2, verified = true))

      val f =
        agreementManagementServiceImpl
          .verifyAttributes(consumerAttributesIds, eserviceAttributes, agreementVerifiedAttributes);

      f.failed.futureValue shouldBe a[RuntimeException]
    }

    "retrieve all verified attributes owned by a consumer if all attributes are verified as true" in {

      val expected: Set[UUID] = Set(
        UUID.fromString(Common.verifiedAttributeId1),
        UUID.fromString(Common.verifiedAttributeId2),
        UUID.fromString(Common.verifiedAttributeId3)
      )

      val f = agreementManagementServiceImpl.extractVerifiedAttribute(agreementsAllTrue)

      f.futureValue shouldBe expected
    }

    "retrieve no attributes if the attributes are verified as false" in {

      val expected: Set[UUID] = Set.empty

      val f = agreementManagementServiceImpl.extractVerifiedAttribute(agreementsAllFalse)

      f.futureValue shouldBe expected
    }

    "retrieve no attributes if each attribute is true/false at the same time" in {

      val expected: Set[UUID] = Set.empty

      val f = agreementManagementServiceImpl.extractVerifiedAttribute(agreementsSameTrueFalse)

      f.futureValue shouldBe expected
    }

    "retrieve all verified attributes owned by a consumer, excluding attributes set true/false at the same time" in {

      val expected: Set[UUID] =
        Set(UUID.fromString(Common.verifiedAttributeId1), UUID.fromString(Common.verifiedAttributeId3))

      val f = agreementManagementServiceImpl.extractVerifiedAttribute(agreementsExcludingFalse)

      f.futureValue shouldBe expected
    }

    "not apply implicit verification" in {

      val expected = Seq(
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = false,
          validityTimespan = None
        ),
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = false,
          validityTimespan = None
        ),
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = false,
          validityTimespan = None
        )
      )

      val f = agreementManagementServiceImpl.applyImplicitVerification(
        verifiedAttributesAllSetTrue,
        customerVerifiedAttributes
      )

      f.futureValue shouldBe expected
    }

    "apply implicit verification" in {

      val expected = Seq(
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = true,
          validityTimespan = None
        ),
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = true,
          validityTimespan = None
        ),
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = false,
          validityTimespan = None
        )
      )

      val f = agreementManagementServiceImpl.applyImplicitVerification(
        verifiedAttributesAllSetFalse,
        customerVerifiedAttributes
      )

      f.futureValue shouldBe expected
    }

    "apply implicit verification only where the explicit verification is not required" in {

      val expected = Seq(
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = false,
          validityTimespan = None
        ),
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = true,
          validityTimespan = None
        ),
        VerifiedAttributeSeed(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = false,
          validityTimespan = None
        )
      )

      val f =
        agreementManagementServiceImpl.applyImplicitVerification(verifiedAttributesMixed, customerVerifiedAttributes)

      f.futureValue shouldBe expected
    }

  }

}
