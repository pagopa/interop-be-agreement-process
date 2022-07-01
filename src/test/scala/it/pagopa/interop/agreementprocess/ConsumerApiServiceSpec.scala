package it.pagopa.interop.agreementprocess

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.agreementprocess.api.impl.{ConsumerApiMarshallerImpl, ConsumerApiServiceImpl}
import it.pagopa.interop.agreementprocess.api.{AgreementApi, ConsumerApi, ConsumerApiMarshaller, HealthApi}
import it.pagopa.interop.agreementprocess.model.{Attribute, Attributes}
import it.pagopa.interop.agreementprocess.server.Controller
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

class ConsumerApiServiceSpec
    extends ScalaTestWithActorTestKit
    with MockFactory
    with AnyWordSpecLike
    with SprayJsonSupport
    with DefaultJsonProtocol
    with SpecHelper {

  implicit val testSystem: ActorSystem        = system.classicSystem
  implicit val ec: ExecutionContextExecutor   = system.executionContext
  implicit val context: Seq[(String, String)] = Seq("bearer" -> Common.bearerToken)

  val consumerApiMarshaller: ConsumerApiMarshaller               = new ConsumerApiMarshallerImpl
  val mockHealthApi: HealthApi                                   = mock[HealthApi]
  val mockAgreementApi: AgreementApi                             = mock[AgreementApi]
  val mockPartyManagementService: PartyManagementService         = mock[PartyManagementService]
  val mockAgreementManagementService: AgreementManagementService = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService     = mock[CatalogManagementService]
  val mockAttributeManagementService: AttributeManagementService = mock[AttributeManagementService]
  val mockJWTReader: JWTReader                                   = mock[JWTReader]

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

  override def beforeAll(): Unit = {

    val consumerApi = new ConsumerApi(
      ConsumerApiServiceImpl(
        agreementManagementService = mockAgreementManagementService,
        partyManagementService = mockPartyManagementService,
        catalogManagementService = mockCatalogManagementService,
        attributeManagementService = mockAttributeManagementService,
        jwtReader = mockJWTReader
      ),
      consumerApiMarshaller,
      wrappingDirective
    )

    controller = Some(new Controller(agreement = mockAgreementApi, health = mockHealthApi, consumer = consumerApi))

    controller foreach { controller =>
      bindServer = Some(
        Http()
          .newServerAt("0.0.0.0", 8088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  override def afterAll(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
  }

  "Processing a consumer request" should {

    "retrieve all attributes owned by a customer (customer with all kind attributes)" in {

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
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          *
        )
        .returns(Future.successful(Seq(TestDataOne.agreement, TestDataTwo.agreement)))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataOne.eserviceId, *)
        .returns(Future.successful(TestDataOne.eService))
        .once()

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataTwo.eserviceId, *)
        .returns(Future.successful(TestDataTwo.eService))
        .once()

      (mockPartyManagementService
        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(UUID.fromString(Common.consumerId), *, *)
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (mockAttributeManagementService
        .getAttributeByOriginAndCode(_: String, _: String)(_: Seq[(String, String)]))
        .expects(Common.certifiedAttribute.origin, Common.certifiedAttribute.code, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.verifiedAttributeId1, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.verifiedAttributeId2, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId2))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.verifiedAttributeId3, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId3))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.declaredAttributeId1, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.declaredAttributeId2, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId2))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.declaredAttributeId3, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId3))

      val response =
        request(data = emptyData, path = s"consumers/${Common.consumerId}/attributes", verb = HttpMethods.GET)

      implicit def attributeJsonFormat: RootJsonFormat[Attribute] = jsonFormat9(Attribute)

      implicit val fromEntityUnmarshallerAttributes: FromEntityUnmarshaller[Attributes] =
        sprayJsonUnmarshaller[Attributes](jsonFormat3(Attributes))

      val body = Await.result(Unmarshal(response.entity).to[Attributes], Duration.Inf)

      body.certified.count(
        _ == AttributeManagementService.toApi(ClientAttributes.certifiedAttribute).futureValue
      ) shouldBe 1

      body.declared.toSet shouldBe Set(
        AttributeManagementService.toApi(ClientAttributes.declaredAttributeId1).futureValue,
        AttributeManagementService.toApi(ClientAttributes.declaredAttributeId2).futureValue,
        AttributeManagementService.toApi(ClientAttributes.declaredAttributeId3).futureValue
      )

      body.verified.toSet shouldBe Set(
        AttributeManagementService.toApi(ClientAttributes.verifiedAttributeId1).futureValue,
        AttributeManagementService.toApi(ClientAttributes.verifiedAttributeId2).futureValue,
        AttributeManagementService.toApi(ClientAttributes.verifiedAttributeId3).futureValue
      )

    }

    "retrieve all attributes owned by a customer (customer without verified attributes)" in {

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
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          *
        )
        .returns(Future.successful(Seq(TestDataOne.agreement, TestDataThree.agreement)))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataOne.eserviceId, *)
        .returns(Future.successful(TestDataOne.eService))
        .once()

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataThree.eserviceId, *)
        .returns(Future.successful(TestDataThree.eService))
        .once()

      (mockPartyManagementService
        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(UUID.fromString(Common.consumerId), *, *)
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (mockAttributeManagementService
        .getAttributeByOriginAndCode(_: String, _: String)(_: Seq[(String, String)]))
        .expects(Common.certifiedAttribute.origin, Common.certifiedAttribute.code, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.declaredAttributeId1, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.declaredAttributeId2, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId2))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.declaredAttributeId3, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId3))

      val response =
        request(data = emptyData, path = s"consumers/${Common.consumerId}/attributes", verb = HttpMethods.GET)

      implicit def attributeJsonFormat: RootJsonFormat[Attribute] = jsonFormat9(Attribute)

      implicit val fromEntityUnmarshallerAttributes: FromEntityUnmarshaller[Attributes] =
        sprayJsonUnmarshaller[Attributes](jsonFormat3(Attributes))

      val body = Await.result(Unmarshal(response.entity).to[Attributes], Duration.Inf)

      body.certified.count(
        _ == AttributeManagementService.toApi(ClientAttributes.certifiedAttribute).futureValue
      ) shouldBe 1

      body.declared.toSet shouldBe Set(
        AttributeManagementService.toApi(ClientAttributes.declaredAttributeId1).futureValue,
        AttributeManagementService.toApi(ClientAttributes.declaredAttributeId2).futureValue,
        AttributeManagementService.toApi(ClientAttributes.declaredAttributeId3).futureValue
      )

      body.verified.toSet shouldBe Set.empty

    }

    "retrieve all attributes owned by a customer (customer without declared attributes)" in {

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
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementManagementDependency.AgreementState.ACTIVE),
          *
        )
        .returns(Future.successful(Seq(TestDataTwo.agreement, TestDataFour.agreement)))

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataTwo.eserviceId, *)
        .returns(Future.successful(TestDataTwo.eService))
        .once()

      (mockCatalogManagementService
        .getEServiceById(_: UUID)(_: Seq[(String, String)]))
        .expects(TestDataFour.eserviceId, *)
        .returns(Future.successful(TestDataFour.eService))
        .once()

      (mockPartyManagementService
        .getPartyAttributes(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(UUID.fromString(Common.consumerId), *, *)
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (mockAttributeManagementService
        .getAttributeByOriginAndCode(_: String, _: String)(_: Seq[(String, String)]))
        .expects(Common.certifiedAttribute.origin, Common.certifiedAttribute.code, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.verifiedAttributeId1, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService
        .getAttribute(_: String)(_: Seq[(String, String)]))
        .expects(Common.verifiedAttributeId3, *)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId3))

      val response                                                =
        request(data = emptyData, path = s"consumers/${Common.consumerId}/attributes", verb = HttpMethods.GET)
      implicit def attributeJsonFormat: RootJsonFormat[Attribute] = jsonFormat9(Attribute)

      implicit val fromEntityUnmarshallerAttributes: FromEntityUnmarshaller[Attributes] =
        sprayJsonUnmarshaller[Attributes](jsonFormat3(Attributes))

      val body = Await.result(Unmarshal(response.entity).to[Attributes], Duration.Inf)

      body.certified.count(
        _ == AttributeManagementService.toApi(ClientAttributes.certifiedAttribute).futureValue
      ) shouldBe 1

      body.declared.toSet shouldBe Set.empty

      body.verified.toSet shouldBe Set(
        AttributeManagementService.toApi(ClientAttributes.verifiedAttributeId1).futureValue,
        AttributeManagementService.toApi(ClientAttributes.verifiedAttributeId3).futureValue
      )

    }

  }

}
