package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.agreementmanagement.client.model.StateChangeDetails
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.agreementprocess.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  ConsumerApiMarshallerImpl
}
import it.pagopa.interop.agreementprocess.api.{
  AgreementApi,
  AgreementApiMarshaller,
  AgreementApiService,
  ConsumerApiMarshaller,
  HealthApi
}
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service.AgreementManagementService.agreementStateToApi
import it.pagopa.interop.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  AuthorizationManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.interop.catalogmanagement.client.model.EServiceDescriptor
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.RootJsonFormat

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}

class AgreementApiServiceSpec extends AnyWordSpecLike with MockFactory with SpecHelper with ScalatestRouteTest {

  val consumerApiMarshaller: ConsumerApiMarshaller                       = new ConsumerApiMarshallerImpl
  val agreementApiMarshaller: AgreementApiMarshaller                     = new AgreementApiMarshallerImpl
  val mockHealthApi: HealthApi                                           = mock[HealthApi]
  val mockAgreementApi: AgreementApi                                     = mock[AgreementApi]
  val mockPartyManagementService: PartyManagementService                 = mock[PartyManagementService]
  val mockAgreementManagementService: AgreementManagementService         = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService             = mock[CatalogManagementService]
  val mockAttributeManagementService: AttributeManagementService         = mock[AttributeManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService = mock[AuthorizationManagementService]
  val mockJWTReader: JWTReader                                           = mock[JWTReader]

  import consumerApiMarshaller._

  val service: AgreementApiService = AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockPartyManagementService,
    mockAttributeManagementService,
    mockAuthorizationManagementService,
    mockJWTReader
  )(ExecutionContext.global)

  "Agreement Activation" should {
    "succeed on pending agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val pendingAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.PENDING)
      val eService         = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            id = pendingAgreement.descriptorId,
            version = "1",
            description = None,
            audience = Seq.empty,
            voucherLifespan = 0,
            interface = None,
            docs = Seq.empty,
            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
            dailyCallsPerConsumer = 1000,
            dailyCallsTotal = 10
          )
        )
      )

      (mockJWTReader
        .getClaims(_: String))
        .expects(*)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(pendingAgreement))

      (
        mockAgreementManagementService
          .getAgreements(_: Seq[(String, String)])(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )
        )
        .expects(
          Common.requestContexts,
          Some(TestDataOne.producerId.toString),
          Some(pendingAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: UUID))
        .expects(Common.bearerToken, pendingAgreement.consumerId)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: Seq[(String, String)])(_: UUID))
        .expects(Common.requestContexts, eService.id)
        .once()
        .returns(Future.successful(eService))

      (mockAgreementManagementService
        .activateById(_: Seq[(String, String)])(_: String, _: StateChangeDetails))
        .expects(
          Common.requestContexts,
          TestDataOne.id.toString,
          StateChangeDetails(changedBy = Some(AgreementManagementDependency.ChangedBy.PRODUCER))
        )
        .once()
        .returns(Future.successful(pendingAgreement))

      (
        mockAuthorizationManagementService
          .updateStateOnClients(_: Seq[(String, String)])(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState
          )
        )
        .expects(
          Common.requestContexts,
          pendingAgreement.eserviceId,
          pendingAgreement.consumerId,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE
        )
        .returning(Future.successful(()))
        .once()

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed on suspended agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val suspendedAgreement                      =
        TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)
      val eService                                = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            id = suspendedAgreement.descriptorId,
            version = "1",
            description = None,
            audience = Seq.empty,
            voucherLifespan = 0,
            interface = None,
            docs = Seq.empty,
            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
            dailyCallsPerConsumer = 1000,
            dailyCallsTotal = 10
          )
        )
      )

      (mockJWTReader
        .getClaims(_: String))
        .expects(*)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(suspendedAgreement))

      (
        mockAgreementManagementService
          .getAgreements(_: Seq[(String, String)])(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )
        )
        .expects(
          Common.requestContexts,
          Some(TestDataOne.producerId.toString),
          Some(suspendedAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: UUID))
        .expects(Common.bearerToken, suspendedAgreement.consumerId)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: Seq[(String, String)])(_: UUID))
        .expects(Common.requestContexts, eService.id)
        .once()
        .returns(Future.successful(eService))

      (mockAgreementManagementService
        .activateById(_: Seq[(String, String)])(_: String, _: StateChangeDetails))
        .expects(
          Common.requestContexts,
          TestDataOne.id.toString,
          StateChangeDetails(changedBy = Some(AgreementManagementDependency.ChangedBy.PRODUCER))
        )
        .once()
        .returns(Future.successful(suspendedAgreement))

      (
        mockAuthorizationManagementService
          .updateStateOnClients(_: Seq[(String, String)])(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState
          )
        )
        .expects(
          Common.requestContexts,
          suspendedAgreement.eserviceId,
          suspendedAgreement.consumerId,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE
        )
        .returning(Future.successful(()))
        .once()

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if missing authorization header" in {
      implicit val contexts: Seq[(String, String)] = Seq.empty[(String, String)]
      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if an active agreement already exists" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)
      val activeAgreement  =
        TestDataOne.agreement.copy(id = UUID.randomUUID(), state = AgreementManagementDependency.AgreementState.ACTIVE)

      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            id = currentAgreement.descriptorId,
            version = "1",
            description = None,
            audience = Seq.empty,
            voucherLifespan = 0,
            interface = None,
            docs = Seq.empty,
            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
            dailyCallsPerConsumer = 1000,
            dailyCallsTotal = 10
          )
        )
      )

      (mockJWTReader
        .getClaims(_: String))
        .expects(*)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      (
        mockAgreementManagementService
          .getAgreements(_: Seq[(String, String)])(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )
        )
        .expects(
          Common.requestContexts,
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE)
        )
        .once()
        .returns(Future.successful(Seq(activeAgreement)))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if agreement is not Pending or Suspended" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.ACTIVE)

      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            id = currentAgreement.descriptorId,
            version = "1",
            description = None,
            audience = Seq.empty,
            voucherLifespan = 0,
            interface = None,
            docs = Seq.empty,
            state = CatalogManagementDependency.EServiceDescriptorState.PUBLISHED,
            dailyCallsPerConsumer = 1000,
            dailyCallsTotal = 10
          )
        )
      )

      (mockJWTReader
        .getClaims(_: String))
        .expects(*)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      (
        mockAgreementManagementService
          .getAgreements(_: Seq[(String, String)])(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )
        )
        .expects(
          Common.requestContexts,
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if descriptor is not Published" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)

      val eService = TestDataOne.eService.copy(descriptors =
        Seq(
          EServiceDescriptor(
            id = currentAgreement.descriptorId,
            version = "1",
            description = None,
            audience = Seq.empty,
            voucherLifespan = 0,
            interface = None,
            docs = Seq.empty,
            state = CatalogManagementDependency.EServiceDescriptorState.DEPRECATED,
            dailyCallsPerConsumer = 1000,
            dailyCallsTotal = 10
          )
        )
      )

      (mockJWTReader
        .getClaims(_: String))
        .expects(*)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      (
        mockAgreementManagementService
          .getAgreements(_: Seq[(String, String)])(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )
        )
        .expects(
          Common.requestContexts,
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE)
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: UUID))
        .expects(Common.bearerToken, currentAgreement.consumerId)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: Seq[(String, String)])(_: UUID))
        .expects(Common.requestContexts, eService.id)
        .once()
        .returns(Future.successful(eService))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

  "Agreement Suspension" should {
    "succeed on active agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val activeAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.ACTIVE)

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(activeAgreement))

      (mockAgreementManagementService
        .suspendById(_: Seq[(String, String)])(_: String, _: StateChangeDetails))
        .expects(
          Common.requestContexts,
          TestDataOne.id.toString,
          StateChangeDetails(Some(AgreementManagementDependency.ChangedBy.PRODUCER))
        )
        .once()
        .returns(Future.successful(activeAgreement))

      (
        mockAuthorizationManagementService
          .updateStateOnClients(_: Seq[(String, String)])(
            _: UUID,
            _: UUID,
            _: AuthorizationManagementDependency.ClientComponentState
          )
        )
        .expects(
          Common.requestContexts,
          activeAgreement.eserviceId,
          activeAgreement.consumerId,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE
        )
        .returning(Future.successful(()))
        .once()

      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if agreement is not Active" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)
      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(currentAgreement))

      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "Agreement GET" should {
    "retrieves an agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)

      (mockJWTReader
        .getClaims(_: String))
        .expects(*)
        .returning(mockSubject(UUID.randomUUID().toString))
        .once()

      (mockAgreementManagementService
        .getAgreementById(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataSeven.agreementId.toString)
        .once()
        .returns(Future.successful(TestDataSeven.agreement))

      (mockCatalogManagementService
        .getEServiceById(_: Seq[(String, String)])(_: UUID))
        .expects(Common.requestContexts, TestDataSeven.eservice.id)
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
        .getAttribute(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataSeven.eservice.attributes.verified(0).single.get.id)
        .once()
        .returns(Future.successful(ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: Seq[(String, String)])(_: String))
        .expects(Common.requestContexts, TestDataSeven.eservice.attributes.verified(1).single.get.id)
        .once()
        .returns(Future.successful(ClientAttributes.verifiedAttributeId2))

      import agreementApiMarshaller._
      import spray.json.DefaultJsonProtocol._

      implicit def organizationJsonFormat: RootJsonFormat[Organization]               = jsonFormat2(Organization)
      implicit def activeDescriptorJsonFormat: RootJsonFormat[ActiveDescriptor]       = jsonFormat3(ActiveDescriptor)
      implicit def eServiceJsonFormat: RootJsonFormat[EService]                       = jsonFormat4(EService)
      implicit def attributeJsonFormat: RootJsonFormat[Attribute]                     = jsonFormat9(Attribute)
      implicit def agreementAttributesJsonFormat: RootJsonFormat[AgreementAttributes] = jsonFormat2(AgreementAttributes)
      implicit def agreementJsonFormat: RootJsonFormat[Agreement]                     = jsonFormat9(Agreement)
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
          activeDescriptor = None
        ),
        eserviceDescriptorId = TestDataSeven.eservice.descriptors(0).id,
        state = agreementStateToApi(TestDataSeven.agreement.state),
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
