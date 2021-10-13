package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.StatusChangeDetailsEnums.ChangedBy.Producer
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{AgreementEnums, StatusChangeDetails}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  ConsumerApiMarshallerImpl,
  localTimeFormat,
  uuidFormat
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.{
  AgreementApi,
  AgreementApiMarshaller,
  ConsumerApiMarshaller,
  HealthApi
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{EServiceDescriptor, EServiceDescriptorEnums}
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.RootJsonFormat

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AgreementApiServiceSpec extends AnyWordSpecLike with MockFactory with SpecHelper with ScalatestRouteTest {

  val consumerApiMarshaller: ConsumerApiMarshaller               = new ConsumerApiMarshallerImpl
  val agreementApiMarshaller: AgreementApiMarshaller             = new AgreementApiMarshallerImpl
  val mockHealthApi: HealthApi                                   = mock[HealthApi]
  val mockAgreementApi: AgreementApi                             = mock[AgreementApi]
  val mockPartyManagementService: PartyManagementService         = mock[PartyManagementService]
  val mockAgreementManagementService: AgreementManagementService = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService     = mock[CatalogManagementService]
  val mockAttributeManagementService: AttributeManagementService = mock[AttributeManagementService]

  import consumerApiMarshaller._

  val service = new AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockPartyManagementService,
    mockAttributeManagementService
  )(ExecutionContext.global)

  implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)

  "Agreement Activation" should {
    "succeed on pending agreement" in {
      val pendingAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Pending)
      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            pendingAgreement.descriptorId,
            "1",
            None,
            Seq.empty,
            0,
            None,
            Seq.empty,
            EServiceDescriptorEnums.Status.Published
          )
        )
      )

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(pendingAgreement))

      (mockAgreementManagementService
        .getAgreements(_: String)(
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String]
        ))
        .expects(
          Common.bearerToken,
          Some(TestDataOne.producerId.toString),
          Some(pendingAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementEnums.Status.Active.toString)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: String))
        .expects(Common.bearerToken, pendingAgreement.consumerId.toString)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, eService.id)
        .once()
        .returns(Future.successful(eService))

      (mockAgreementManagementService
        .activateById(_: String)(_: String, _: StatusChangeDetails))
        .expects(Common.bearerToken, TestDataOne.id.toString, StatusChangeDetails(changedBy = Some(Producer)))
        .once()
        .returns(Future.successful(pendingAgreement))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed on suspended agreement" in {
      val suspendedAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Suspended)
      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            suspendedAgreement.descriptorId,
            "1",
            None,
            Seq.empty,
            0,
            None,
            Seq.empty,
            EServiceDescriptorEnums.Status.Published
          )
        )
      )

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(suspendedAgreement))

      (mockAgreementManagementService
        .getAgreements(_: String)(
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String]
        ))
        .expects(
          Common.bearerToken,
          Some(TestDataOne.producerId.toString),
          Some(suspendedAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementEnums.Status.Active.toString)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: String))
        .expects(Common.bearerToken, suspendedAgreement.consumerId.toString)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, eService.id)
        .once()
        .returns(Future.successful(eService))

      (mockAgreementManagementService
        .activateById(_: String)(_: String, _: StatusChangeDetails))
        .expects(Common.bearerToken, TestDataOne.id.toString, StatusChangeDetails(changedBy = Some(Producer)))
        .once()
        .returns(Future.successful(suspendedAgreement))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if missing authorization header" in {
      val contexts: Seq[(String, String)] = Seq.empty[(String, String)]
      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString)(
        toEntityMarshallerProblem,
        contexts
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if an active agreement already exists" in {
      val currentAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Suspended)
      val activeAgreement  = TestDataOne.agreement.copy(id = UUID.randomUUID(), status = AgreementEnums.Status.Active)

      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            currentAgreement.descriptorId,
            "1",
            None,
            Seq.empty,
            0,
            None,
            Seq.empty,
            EServiceDescriptorEnums.Status.Published
          )
        )
      )

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      (mockAgreementManagementService
        .getAgreements(_: String)(
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String]
        ))
        .expects(
          Common.bearerToken,
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementEnums.Status.Active.toString)
        )
        .once()
        .returns(Future.successful(Seq(activeAgreement)))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if agreement is not Pending or Suspended" in {
      val currentAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Active)

      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            currentAgreement.descriptorId,
            "1",
            None,
            Seq.empty,
            0,
            None,
            Seq.empty,
            EServiceDescriptorEnums.Status.Published
          )
        )
      )

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      (mockAgreementManagementService
        .getAgreements(_: String)(
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String]
        ))
        .expects(
          Common.bearerToken,
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementEnums.Status.Active.toString)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if descriptor is not Published" in {
      val currentAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Suspended)

      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            currentAgreement.descriptorId,
            "1",
            None,
            Seq.empty,
            0,
            None,
            Seq.empty,
            EServiceDescriptorEnums.Status.Deprecated
          )
        )
      )

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      (mockAgreementManagementService
        .getAgreements(_: String)(
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String]
        ))
        .expects(
          Common.bearerToken,
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementEnums.Status.Active.toString)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: String))
        .expects(Common.bearerToken, currentAgreement.consumerId.toString)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, eService.id)
        .once()
        .returns(Future.successful(eService))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

  "Agreement Suspension" should {
    "succeed on active agreement" in {
      val activeAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Active)

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(activeAgreement))

      (mockAgreementManagementService
        .suspendById(_: String)(_: String, _: StatusChangeDetails))
        .expects(Common.bearerToken, TestDataOne.id.toString, StatusChangeDetails(Some(Producer)))
        .once()
        .returns(Future.successful(activeAgreement))

      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if missing authorization header" in {
      val contexts: Seq[(String, String)] = Seq.empty[(String, String)]
      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString)(
        toEntityMarshallerProblem,
        contexts
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if agreement is not Active" in {
      val currentAgreement = TestDataOne.agreement.copy(status = AgreementEnums.Status.Suspended)

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "Agreement GET" should {
    "retrieves an agreement" in {

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataSeven.agreementId.toString)
        .once()
        .returns(Future.successful(TestDataSeven.agreement))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataSeven.eservice.id)
        .once()
        .returns(Future.successful(TestDataSeven.eservice))

      (mockPartyManagementService
        .getOrganization(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataSeven.producerId)
        .once()
        .returns(Future.successful(TestDataSeven.producer))

      (mockPartyManagementService
        .getOrganization(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataSeven.consumerId)
        .once()
        .returns(Future.successful(TestDataSeven.consumer))

      (mockAttributeManagementService
        .getAttribute(_: String))
        .expects(TestDataSeven.eservice.attributes.verified(0).single.get.id)
        .once()
        .returns(Future.successful(ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: String))
        .expects(TestDataSeven.eservice.attributes.verified(1).single.get.id)
        .once()
        .returns(Future.successful(ClientAttributes.verifiedAttributeId2))

      import agreementApiMarshaller._
      import spray.json.DefaultJsonProtocol._

      implicit def organizationJsonFormat: RootJsonFormat[Organization]               = jsonFormat2(Organization)
      implicit def activeDescriptorJsonFormat: RootJsonFormat[ActiveDescriptor]       = jsonFormat3(ActiveDescriptor)
      implicit def eServiceJsonFormat: RootJsonFormat[EService]                       = jsonFormat5(EService)
      implicit def attributeJsonFormat: RootJsonFormat[Attribute]                     = jsonFormat9(Attribute)
      implicit def agreementAttributesJsonFormat: RootJsonFormat[AgreementAttributes] = jsonFormat2(AgreementAttributes)
      implicit def agreementJsonFormat: RootJsonFormat[Agreement]                     = jsonFormat8(Agreement)
      import SprayJsonSupport.sprayJsonUnmarshaller

      implicit def fromEntityUnmarshallerAgreement: FromEntityUnmarshaller[Agreement] =
        sprayJsonUnmarshaller[Agreement]

      val expected = Agreement(
        id = TestDataSeven.agreementId,
        producer = Organization(id = TestDataSeven.producer.institutionId, name = TestDataSeven.producer.description),
        consumer = Organization(id = TestDataSeven.consumer.institutionId, name = TestDataSeven.consumer.description),
        eservice = EService(
          id = TestDataSeven.eservice.id,
          name = TestDataSeven.eservice.name,
          version = TestDataSeven.eservice.descriptors(0).version,
          descriptorId = TestDataSeven.eservice.descriptors(0).id,
          activeDescriptor = None
        ),
        status = TestDataSeven.agreement.status.toString,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        attributes = Seq(
          AgreementAttributes(
            single = Some(
              Attribute(
                id = UUID.fromString(ClientAttributes.verifiedAttributeId1.id),
                code = ClientAttributes.verifiedAttributeId1.code,
                description = ClientAttributes.verifiedAttributeId1.description,
                origin = ClientAttributes.verifiedAttributeId1.origin,
                name = ClientAttributes.verifiedAttributeId1.name,
                explicitAttributeVerification =
                  Some(TestDataSeven.eservice.attributes.verified(0).single.get.explicitAttributeVerification),
                verified = TestDataSeven.agreement.verifiedAttributes(0).verified,
                verificationDate = TestDataSeven.agreement.verifiedAttributes(0).verificationDate,
                validityTimespan = TestDataSeven.agreement.verifiedAttributes(0).validityTimespan
              )
            ),
            group = None
          ),
          AgreementAttributes(
            single = Some(
              Attribute(
                id = UUID.fromString(ClientAttributes.verifiedAttributeId2.id),
                code = ClientAttributes.verifiedAttributeId2.code,
                description = ClientAttributes.verifiedAttributeId2.description,
                origin = ClientAttributes.verifiedAttributeId2.origin,
                name = ClientAttributes.verifiedAttributeId2.name,
                explicitAttributeVerification =
                  Some(TestDataSeven.eservice.attributes.verified(1).single.get.explicitAttributeVerification),
                verified = TestDataSeven.agreement.verifiedAttributes(1).verified,
                verificationDate = TestDataSeven.agreement.verifiedAttributes(1).verificationDate,
                validityTimespan = TestDataSeven.agreement.verifiedAttributes(1).validityTimespan
              )
            ),
            group = None
          )
        )
      )

      Get() ~> service.getAgreementById(TestDataSeven.agreementId.toString) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Agreement] shouldEqual expected
      }
    }

  }

}
