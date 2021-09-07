package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.actor.ClassicActorSystemProvider
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  AgreementEnums,
  VerifiedAttribute,
  Agreement => ClientAgreement
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.{Attribute => ClientAttribute}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, AttributeValue, Attributes, EService}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait SpecHelper {

  final lazy val url: String                        = "http://localhost:8088/pdnd-interop-uservice-agreement-process/0.0.1"
  final lazy val emptyData: Source[ByteString, Any] = Source.empty
  final val authorization: Seq[Authorization]       = Seq(headers.Authorization(OAuth2BearerToken(Common.bearerToken)))

  def request(data: Source[ByteString, Any], path: String, verb: HttpMethod)(implicit
    system: ClassicActorSystemProvider
  ): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"$url/$path",
          method = verb,
          entity = HttpEntity(ContentTypes.`application/json`, data),
          headers = authorization
        )
      ),
      Duration.Inf
    )
  }

  object Common {
    val bearerToken: String          = "bearerToken"
    val consumerId: String           = "07f8dce0-0a5b-476b-9fdd-a7a658eb9213"
    val verifiedAttributeId1: String = "07f8dce0-0a5b-476b-9fdd-a7a658eb9214"
    val verifiedAttributeId2: String = "07f8dce0-0a5b-476b-9fdd-a7a658eb9215"
    val verifiedAttributeId3: String = "07f8dce0-0a5b-476b-9fdd-a7a658eb9216"
    val certifiedAttribute: String   = "07f8dce0-0a5b-476b-9fdd-a7a658eb9217"
    val declaredAttributeId1: String = "17f8dce0-0a5b-476b-9fdd-a7a658eb9213"
    val declaredAttributeId2: String = "17f8dce0-0a5b-476b-9fdd-a7a658eb9214"
    val declaredAttributeId3: String = "17f8dce0-0a5b-476b-9fdd-a7a658eb9215"
    val declaredAttributeId4: String = "27f8dce0-0a5b-476b-9fdd-a7a658eb9213"

  }

  object TestDataOne {
    val id: UUID         = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        )
      )
    )

    val declaredAttributes: Seq[Attribute] = Seq(
      Attribute(single = Some(AttributeValue(Common.declaredAttributeId1, false))),
      Attribute(group =
        Some(
          Seq(AttributeValue(Common.declaredAttributeId2, false), AttributeValue(Common.declaredAttributeId3, false))
        )
      )
    )

    val eService: EService = EService(
      id = eserviceId,
      producerId = producerId,
      name = "",
      description = "",
      audience = Seq.empty[String],
      technology = "",
      voucherLifespan = 1,
      attributes = Attributes(certified = Seq.empty, declared = declaredAttributes, verified = Seq.empty),
      descriptors = Seq.empty
    )

  }

  object TestDataTwo {
    val id: UUID         = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        )
      )
    )

    val declaredAttributes: Seq[Attribute] = Seq(
      Attribute(single = Some(AttributeValue(Common.declaredAttributeId4, false)))
    )

    val eService: EService = EService(
      id = eserviceId,
      producerId = producerId,
      name = "",
      description = "",
      audience = Seq.empty[String],
      technology = "",
      voucherLifespan = 1,
      attributes = Attributes(certified = Seq.empty, declared = declaredAttributes, verified = Seq.empty),
      descriptors = Seq.empty
    )

  }

  object TestDataThree {
    val id: UUID         = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = false,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = false,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = false,
          verificationDate = None,
          validityTimespan = None
        )
      )
    )
  }

  object TestDataFour {
    val id: UUID         = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("4f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = false,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = true,
          verificationDate = None,
          validityTimespan = None
        )
      )
    )
  }

  val agreementsAllTrue: Seq[ClientAgreement]        = Seq(TestDataOne.agreement, TestDataTwo.agreement)
  val agreementsAllFalse: Seq[ClientAgreement]       = Seq(TestDataThree.agreement)
  val agreementsSameTrueFalse: Seq[ClientAgreement]  = Seq(TestDataOne.agreement, TestDataThree.agreement)
  val agreementsExcludingFalse: Seq[ClientAgreement] = Seq(TestDataOne.agreement, TestDataFour.agreement)

  val verifiedAttributesAllSetFalse = Seq(
    AttributeValue(id = Common.verifiedAttributeId1, explicitAttributeVerification = false),
    AttributeValue(id = Common.verifiedAttributeId2, explicitAttributeVerification = false),
    AttributeValue(id = Common.verifiedAttributeId3, explicitAttributeVerification = false)
  )

  val verifiedAttributesAllSetTrue = Seq(
    AttributeValue(id = Common.verifiedAttributeId1, explicitAttributeVerification = true),
    AttributeValue(id = Common.verifiedAttributeId2, explicitAttributeVerification = true),
    AttributeValue(id = Common.verifiedAttributeId3, explicitAttributeVerification = true)
  )

  val verifiedAttributesMixed = Seq(
    AttributeValue(id = Common.verifiedAttributeId1, explicitAttributeVerification = true),
    AttributeValue(id = Common.verifiedAttributeId2, explicitAttributeVerification = false),
    AttributeValue(id = Common.verifiedAttributeId3, explicitAttributeVerification = true)
  )

  val customerVerifiedAttributes =
    Set(UUID.fromString(Common.verifiedAttributeId1), UUID.fromString(Common.verifiedAttributeId2))

  object ClientAttributes {
    val verifiedAttributeId1: ClientAttribute = ClientAttribute(
      id = Common.verifiedAttributeId1,
      code = Some("ver1"),
      certified = false,
      description = "ver1",
      origin = Some("ver1"),
      name = "ver1",
      creationTime = OffsetDateTime.now()
    )
    val verifiedAttributeId2: ClientAttribute = ClientAttribute(
      id = Common.verifiedAttributeId2,
      code = Some("ver2"),
      certified = false,
      description = "ver2",
      origin = Some("ver2"),
      name = "ver2",
      creationTime = OffsetDateTime.now()
    )
    val verifiedAttributeId3: ClientAttribute = ClientAttribute(
      id = Common.verifiedAttributeId3,
      code = Some("ver3"),
      certified = false,
      description = "ver3",
      origin = Some("ver3"),
      name = "ver3",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId1: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId1,
      code = Some("dec1"),
      certified = false,
      description = "dec1",
      origin = Some("dec1"),
      name = "dec1",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId2: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId2,
      code = Some("dec2"),
      certified = false,
      description = "dec2",
      origin = Some("dec2"),
      name = "dec2",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId3: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId3,
      code = Some("dec3"),
      certified = false,
      description = "dec3",
      origin = Some("dec3"),
      name = "dec3",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId4: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId3,
      code = Some("dec4"),
      certified = false,
      description = "dec4",
      origin = Some("dec4"),
      name = "dec4",
      creationTime = OffsetDateTime.now()
    )
    val certifiedAttribute: ClientAttribute = ClientAttribute(
      id = Common.certifiedAttribute,
      code = Some("cer"),
      certified = true,
      description = "cer",
      origin = Some("cer"),
      name = "cer",
      creationTime = OffsetDateTime.now()
    )
  }

}
