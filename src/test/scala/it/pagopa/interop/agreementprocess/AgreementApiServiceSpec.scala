package it.pagopa.interop.agreementprocess

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.testkit._
import it.pagopa.interop.agreementmanagement.client.model.{AgreementDocumentSeed, StateChangeDetails}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.agreementprocess.api._
import it.pagopa.interop.agreementprocess.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  ConsumerApiMarshallerImpl
}
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementprocess.service.AgreementManagementService.agreementStateToApi
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl.AgreementManagementServiceImpl
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagementDependency}
import it.pagopa.interop.catalogmanagement.client.model.EServiceDescriptor
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.USER_ROLES
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.RootJsonFormat

import java.io.{ByteArrayOutputStream, File, FileInputStream, InputStream}
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class AgreementApiServiceSpec extends AnyWordSpecLike with MockFactory with SpecHelper with ScalatestRouteTest {

  val consumerApiMarshaller: ConsumerApiMarshaller                       = new ConsumerApiMarshallerImpl
  val agreementApiMarshaller: AgreementApiMarshaller                     = AgreementApiMarshallerImpl
  val mockHealthApi: HealthApi                                           = mock[HealthApi]
  val mockAgreementApi: AgreementApi                                     = mock[AgreementApi]
  val mockPartyManagementService: PartyManagementService                 = mock[PartyManagementService]
  val mockAgreementManagementService: AgreementManagementService         = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService             = mock[CatalogManagementService]
  val mockAttributeManagementService: AttributeManagementService         = mock[AttributeManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService = mock[AuthorizationManagementService]
  val mockJWTReader: JWTReader                                           = mock[JWTReader]
  val mockFileManager: FileManager                                       = mock[FileManager]
  val mockPDFCreator: PDFCreator                                         = mock[PDFCreator]
  val mockUUIDSupplier: UUIDSupplier                                     = mock[UUIDSupplier]

  import consumerApiMarshaller._
  val service: AgreementApiService = AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockPartyManagementService,
    mockAttributeManagementService,
    mockAuthorizationManagementService,
    mockJWTReader,
    mockFileManager,
    mockPDFCreator,
    mockUUIDSupplier
  )(ExecutionContext.global)

  "Agreement Activation" should {
    "succeed on pending agreement" in {

      implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second.dilated(system))

      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
      val pendingAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.PENDING)
      val eService         = TestDataOne.eService.copy(
        name = "Fake Eservice for Test",
        descriptors = Seq(
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

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(pendingAgreement))

      (
        mockAgreementManagementService
          .getAgreements(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )(_: Seq[(String, String)])
        )
        .expects(
          Some(TestDataOne.producerId.toString),
          Some(pendingAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(pendingAgreement.producerId, Common.requestContexts, *)
        .once()
        .returns(Future.successful(TestDataOne.producer.copy(description = "Ente Producer")))

      (mockPartyManagementService
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(pendingAgreement.consumerId, Common.requestContexts, *)
        .once()
        .returns(Future.successful(TestDataOne.consumer.copy(description = "Ente Consumer")))

      (mockPDFCreator
        .create(_: String, _: String, _: String, _: String))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(TestDataOne.file))

      (mockFileManager
        .store(_: String, _: String)(_: UUID, _: (FileInfo, File)))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful("path"))

      (() => mockUUIDSupplier.get)
        .expects()
        .once()
        .returns(TestDataOne.documentId)

      (mockAgreementManagementService
        .addAgreementDocument(_: String, _: AgreementDocumentSeed)(_: Seq[(String, String)]))
        .expects(TestDataOne.agreement.id.toString, TestDataOne.agreementDocumentSeed, Common.requestContexts)
        .once()
        .returns(Future.unit)

      (mockPartyManagementService
        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(pendingAgreement.consumerId, *, *)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, Common.requestContexts)
        .once()
        .returns(Future.successful(eService))

      (mockAgreementManagementService
        .activateById(_: String, _: StateChangeDetails)(_: Seq[(String, String)]))
        .expects(
          TestDataOne.id.toString,
          StateChangeDetails(changedBy = Some(AgreementManagementDependency.ChangedBy.PRODUCER)),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(pendingAgreement))

      (
        mockAuthorizationManagementService
          .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: AuthorizationManagementDependency.ClientComponentState)(
            _: Seq[(String, String)]
          )
        )
        .expects(
          pendingAgreement.eserviceId,
          pendingAgreement.consumerId,
          pendingAgreement.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          Common.requestContexts
        )
        .returning(Future.successful(()))
        .once()

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "succeed on suspended agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
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

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(suspendedAgreement))

      (
        mockAgreementManagementService
          .getAgreements(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )(_: Seq[(String, String)])
        )
        .expects(
          Some(TestDataOne.producerId.toString),
          Some(suspendedAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(suspendedAgreement.producerId, Common.requestContexts, *)
        .once()
        .returns(Future.successful(TestDataOne.producer))

      (mockPartyManagementService
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(suspendedAgreement.consumerId, Common.requestContexts, *)
        .once()
        .returns(Future.successful(TestDataOne.consumer))

      (mockPDFCreator
        .create(_: String, _: String, _: String, _: String))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(TestDataOne.file))

      (mockFileManager
        .store(_: String, _: String)(_: UUID, _: (FileInfo, File)))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful("path"))

      (() => mockUUIDSupplier.get)
        .expects()
        .once()
        .returns(TestDataOne.documentId)

      (mockAgreementManagementService
        .addAgreementDocument(_: String, _: AgreementDocumentSeed)(_: Seq[(String, String)]))
        .expects(TestDataOne.agreement.id.toString, TestDataOne.agreementDocumentSeed, Common.requestContexts)
        .once()
        .returns(Future.unit)

      (mockPartyManagementService
        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(suspendedAgreement.consumerId, *, *)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, Common.requestContexts)
        .once()
        .returns(Future.successful(eService))

      (mockAgreementManagementService
        .activateById(_: String, _: StateChangeDetails)(_: Seq[(String, String)]))
        .expects(
          TestDataOne.id.toString,
          StateChangeDetails(changedBy = Some(AgreementManagementDependency.ChangedBy.PRODUCER)),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(suspendedAgreement))

      (
        mockAuthorizationManagementService
          .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: AuthorizationManagementDependency.ClientComponentState)(
            _: Seq[(String, String)]
          )
        )
        .expects(
          suspendedAgreement.eserviceId,
          suspendedAgreement.consumerId,
          suspendedAgreement.id,
          AuthorizationManagementDependency.ClientComponentState.ACTIVE,
          Common.requestContexts
        )
        .returning(Future.successful(()))
        .once()

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if missing authorization header" in {
      implicit val contexts: Seq[(String, String)] = Seq.empty[(String, String)]
      val service: AgreementApiService             = AgreementApiServiceImpl(
        AgreementManagementServiceImpl(AgreementManagementInvoker(), AgreementManagementApi(url)),
        mockCatalogManagementService,
        mockPartyManagementService,
        mockAttributeManagementService,
        mockAuthorizationManagementService,
        mockJWTReader,
        mockFileManager,
        mockPDFCreator,
        mockUUIDSupplier
      )(ExecutionContext.global)
      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "fail if an active agreement already exists" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
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

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(currentAgreement))

      (
        mockAgreementManagementService
          .getAgreements(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )(_: Seq[(String, String)])
        )
        .expects(
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(Seq(activeAgreement)))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if agreement is not Pending or Suspended" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
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

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(currentAgreement))

      (
        mockAgreementManagementService
          .getAgreements(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )(_: Seq[(String, String)])
        )
        .expects(
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(Seq.empty))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "fail if descriptor is not Published" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
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

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(currentAgreement))

      (
        mockAgreementManagementService
          .getAgreements(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementState]
          )(_: Seq[(String, String)])
        )
        .expects(
          Some(TestDataOne.producerId.toString),
          Some(currentAgreement.consumerId.toString),
          Some(eService.id.toString),
          Some(TestDataOne.descriptorId.toString),
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(Seq.empty))

      (mockPartyManagementService
        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(currentAgreement.consumerId, *, *)
        .once()
        .returns(Future.successful(Seq.empty))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(eService.id, Common.requestContexts)
        .once()
        .returns(Future.successful(eService))

      Get() ~> service.activateAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

  "Agreement Suspension" should {
    "succeed on active agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
      val activeAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.ACTIVE)

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(activeAgreement))

      (mockAgreementManagementService
        .suspendById(_: String, _: StateChangeDetails)(_: Seq[(String, String)]))
        .expects(
          TestDataOne.id.toString,
          StateChangeDetails(Some(AgreementManagementDependency.ChangedBy.PRODUCER)),
          Common.requestContexts
        )
        .once()
        .returns(Future.successful(activeAgreement))

      (
        mockAuthorizationManagementService
          .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: AuthorizationManagementDependency.ClientComponentState)(
            _: Seq[(String, String)]
          )
        )
        .expects(
          activeAgreement.eserviceId,
          activeAgreement.consumerId,
          activeAgreement.id,
          AuthorizationManagementDependency.ClientComponentState.INACTIVE,
          Common.requestContexts
        )
        .returning(Future.successful(()))
        .once()

      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if agreement is not Active" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")
      val currentAgreement = TestDataOne.agreement.copy(state = AgreementManagementDependency.AgreementState.SUSPENDED)

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataOne.id.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(currentAgreement))

      Get() ~> service.suspendAgreement(TestDataOne.id.toString, TestDataOne.producerId.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  "Agreement GET" should {
    "retrieves an agreement" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataSeven.agreementId.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(TestDataSeven.agreement))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataSeven.eservice.id, Common.requestContexts)
        .once()
        .returns(Future.successful(TestDataSeven.eservice))

      (mockPartyManagementService
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(TestDataSeven.producerId, *, *)
        .once()
        .returns(Future.successful(TestDataSeven.producer))

      (mockPartyManagementService
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(TestDataSeven.consumerId, *, *)
        .once()
        .returns(Future.successful(TestDataSeven.consumer))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(TestDataSeven.eservice.attributes.verified.head.single.get.id, Common.requestContexts)
        .once()
        .returns(Future.successful(ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(TestDataSeven.eservice.attributes.verified(1).single.get.id, Common.requestContexts)
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
        producer = Organization(id = TestDataSeven.producer.originId, name = TestDataSeven.producer.description),
        consumer = Organization(id = TestDataSeven.consumer.originId, name = TestDataSeven.consumer.description),
        eservice = EService(
          id = TestDataSeven.eservice.id,
          name = TestDataSeven.eservice.name,
          version = TestDataSeven.eservice.descriptors.head.version,
          activeDescriptor = None
        ),
        eserviceDescriptorId = TestDataSeven.eservice.descriptors.head.id,
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
                  Some(TestDataSeven.eservice.attributes.verified.head.single.get.explicitAttributeVerification),
                verified = TestDataSeven.agreement.verifiedAttributes.head.verified,
                verificationDate = TestDataSeven.agreement.verifiedAttributes.head.verificationDate,
                validityTimespan = TestDataSeven.agreement.verifiedAttributes.head.validityTimespan
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

  "Agreement Document GET" should {
    "retrieves an agreement document" in {
      implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken, USER_ROLES -> "admin")

      (mockAgreementManagementService
        .getAgreementById(_: String)(_: Seq[(String, String)]))
        .expects(TestDataEight.agreementId.toString, Common.requestContexts)
        .once()
        .returns(Future.successful(TestDataEight.agreement))

      val inputStream: InputStream            = new FileInputStream("src/test/resources/test_file.pdf")
      val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()

      inputStream.transferTo(outputStream)

      (mockFileManager
        .get(_: String)(_: String))
        .expects(*, *)
        .once()
        .returns(Future.successful(outputStream))

      import agreementApiMarshaller._

      Get() ~> service.getAgreementDocument(
        TestDataEight.agreementId.toString,
        documentId = TestDataEight.documentId.toString
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Array[Byte]] shouldEqual outputStream.toByteArray
      }
    }

  }

}
