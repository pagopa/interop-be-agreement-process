package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.actor.ClassicActorSystemProvider
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.nimbusds.jwt.JWTClaimsSet
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  VerifiedAttribute,
  Agreement => ClientAgreement
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.{Attribute => ClientAttribute}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model._
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

trait SpecHelper {

  final lazy val url: String =
    s"http://localhost:8088/pdnd-interop-uservice-agreement-process/${buildinfo.BuildInfo.interfaceVersion}"
  final lazy val emptyData: Source[ByteString, Any] = Source.empty
  final val authorization: Seq[Authorization]       = Seq(headers.Authorization(OAuth2BearerToken(Common.bearerToken)))

  System.setProperty("CATALOG_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("AGREEMENT_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("PARTY_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("ATTRIBUTE_REGISTRY_MANAGEMENT_URL", "http://localhost/")
  System.setProperty("WELL_KNOWN_URL", "http://localhost/.well-known/jwks.json")

  def mockSubject(uuid: String) = Success(new JWTClaimsSet.Builder().subject(uuid).build())

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
    val id: UUID           = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID   = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID   = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val descriptorId: UUID = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9213")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      state = AgreementManagementDependency.AgreementState.ACTIVE,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = Some(true),
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
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      attributes = Attributes(certified = Seq.empty, declared = declaredAttributes, verified = Seq.empty),
      descriptors = Seq.empty
    )

  }

  object TestDataTwo {
    val id: UUID           = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID   = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID   = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val descriptorId: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9213")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      state = AgreementManagementDependency.AgreementState.ACTIVE,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        )
      )
    )

    val eService: EService = EService(
      id = eserviceId,
      producerId = producerId,
      name = "",
      description = "",
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty),
      descriptors = Seq.empty
    )

  }

  object TestDataThree {
    val id: UUID           = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID   = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID   = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val descriptorId: UUID = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9213")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      state = AgreementManagementDependency.AgreementState.ACTIVE,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = Some(false),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = Some(false),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = None,
          verificationDate = None,
          validityTimespan = None
        )
      )
    )

    val eService: EService = EService(
      id = eserviceId,
      producerId = producerId,
      name = "",
      description = "",
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty),
      descriptors = Seq.empty
    )
  }

  object TestDataFour {
    val id: UUID           = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID   = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID   = UUID.fromString("4f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val descriptorId: UUID = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9213")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      state = AgreementManagementDependency.AgreementState.ACTIVE,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = Some(false),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        )
      )
    )

    val eService: EService = EService(
      id = eserviceId,
      producerId = producerId,
      name = "",
      description = "",
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty),
      descriptors = Seq.empty
    )
  }

  object TestDataFive {
    val id: UUID           = UUID.fromString("57f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID   = UUID.fromString("57f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID   = UUID.fromString("5f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val descriptorId: UUID = UUID.fromString("57f8dce0-0a5b-476b-9fdd-a7a658eb9213")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      state = AgreementManagementDependency.AgreementState.PENDING,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = Some(false),
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId3),
          verified = None,
          verificationDate = None,
          validityTimespan = None
        )
      )
    )
  }

  object TestDataSix {
    val id: UUID           = UUID.fromString("67f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID   = UUID.fromString("67f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID   = UUID.fromString("6f8dce0-0a5b-476b-9fdd-a7a658eb9212")
    val descriptorId: UUID = UUID.fromString("67f8dce0-0a5b-476b-9fdd-a7a658eb9213")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = UUID.fromString(Common.consumerId),
      state = AgreementManagementDependency.AgreementState.SUSPENDED,
      verifiedAttributes = Seq.empty
    )
  }

  object TestDataSeven {
    val agreementId: UUID  = UUID.randomUUID()
    val eserviceId: UUID   = UUID.randomUUID()
    val descriptorId: UUID = UUID.randomUUID()
    val producerId: UUID   = UUID.randomUUID()
    val consumerId: UUID   = UUID.randomUUID()

    val consumer = Organization(
      institutionId = "institutionIdC",
      description = "Consumer",
      digitalAddress = "digitalAddress",
      id = consumerId,
      attributes = Seq.empty,
      taxCode = "code"
    )

    val producer = Organization(
      institutionId = "institutionIdP",
      description = "Producer",
      digitalAddress = "digitalAddress",
      id = producerId,
      attributes = Seq.empty,
      taxCode = "code"
    )

    val eservice: EService = EService(
      id = eserviceId,
      producerId = producerId,
      name = "name",
      description = "description",
      technology = CatalogManagementDependency.EServiceTechnology.REST,
      attributes = Attributes(
        certified = Seq.empty,
        declared = Seq.empty,
        verified = Seq(
          Attribute(
            single = Some(AttributeValue(id = Common.verifiedAttributeId1, explicitAttributeVerification = true)),
            group = None
          ),
          Attribute(
            single = Some(AttributeValue(id = Common.verifiedAttributeId2, explicitAttributeVerification = false)),
            group = None
          )
        )
      ),
      descriptors = Seq(
        EServiceDescriptor(
          id = descriptorId,
          version = "1",
          description = None,
          audience = Seq.empty,
          voucherLifespan = 1,
          interface = None,
          docs = Seq.empty,
          state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED
        )
      )
    )

    val agreement: ClientAgreement = ClientAgreement(
      id = agreementId,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      producerId = producerId,
      consumerId = consumerId,
      state = AgreementManagementDependency.AgreementState.PENDING,
      verifiedAttributes = Seq(
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId1),
          verified = None,
          verificationDate = None,
          validityTimespan = None
        ),
        VerifiedAttribute(
          id = UUID.fromString(Common.verifiedAttributeId2),
          verified = Some(false),
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
      code = Some("codeVer1"),
      certified = false,
      description = "descriptionVer1",
      origin = Some("originVer1"),
      name = "nameVer1",
      creationTime = OffsetDateTime.now()
    )
    val verifiedAttributeId2: ClientAttribute = ClientAttribute(
      id = Common.verifiedAttributeId2,
      code = Some("codeVer2"),
      certified = false,
      description = "descriptionVer2",
      origin = Some("originVer2"),
      name = "nameVer2",
      creationTime = OffsetDateTime.now()
    )
    val verifiedAttributeId3: ClientAttribute = ClientAttribute(
      id = Common.verifiedAttributeId3,
      code = Some("codeVer3"),
      certified = false,
      description = "descriptionVer3",
      origin = Some("originVer3"),
      name = "nameVer3",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId1: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId1,
      code = Some("codeDec1"),
      certified = false,
      description = "descriptionDec1",
      origin = Some("originDec1"),
      name = "originDec1",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId2: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId2,
      code = Some("codeDec2"),
      certified = false,
      description = "descriptionDec2",
      origin = Some("originDec2"),
      name = "originDec2",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId3: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId3,
      code = Some("codeDec3"),
      certified = false,
      description = "descriptionDec3",
      origin = Some("originDec3"),
      name = "originDec3",
      creationTime = OffsetDateTime.now()
    )
    val declaredAttributeId4: ClientAttribute = ClientAttribute(
      id = Common.declaredAttributeId3,
      code = Some("codeDec4"),
      certified = false,
      description = "descriptionDec4",
      origin = Some("originDec4"),
      name = "originDec4",
      creationTime = OffsetDateTime.now()
    )
    val certifiedAttribute: ClientAttribute = ClientAttribute(
      id = Common.certifiedAttribute,
      code = Some("codeCer"),
      certified = true,
      description = "descriptionCer",
      origin = Some("originCer"),
      name = "nameCer",
      creationTime = OffsetDateTime.now()
    )
  }

}
