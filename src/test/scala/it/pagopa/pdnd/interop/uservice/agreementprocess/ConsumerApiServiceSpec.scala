package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{
  ConsumerApiMarshallerImpl,
  ConsumerApiServiceImpl,
  localTimeFormat,
  uuidFormat
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.{
  AgreementApi,
  ConsumerApi,
  ConsumerApiMarshaller,
  HealthApi
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.common.system.{Authenticator, executionContext}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Attribute, Attributes}
import it.pagopa.pdnd.interop.uservice.agreementprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementprocess.service._
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

class ConsumerApiServiceSpec
    extends ScalaTestWithActorTestKit
    with MockFactory
    with AnyWordSpecLike
    with SprayJsonSupport
    with DefaultJsonProtocol
    with SpecHelper {

  implicit val testSystem: ActorSystem = system.classicSystem

  val consumerApiMarshaller: ConsumerApiMarshaller               = new ConsumerApiMarshallerImpl
  val mockHealthApi: HealthApi                                   = mock[HealthApi]
  val mockAgreementApi: AgreementApi                             = mock[AgreementApi]
  val mockPartyManagementService: PartyManagementService         = mock[PartyManagementService]
  val mockAgreementManagementService: AgreementManagementService = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService     = mock[CatalogManagementService]
  val mockAttributeManagementService: AttributeManagementService = mock[AttributeManagementService]

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  override def beforeAll(): Unit = {

    val consumerApi = new ConsumerApi(
      new ConsumerApiServiceImpl(
        agreementManagementService = mockAgreementManagementService,
        partyManagementService = mockPartyManagementService,
        catalogManagementService = mockCatalogManagementService,
        attributeManagementService = mockAttributeManagementService
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
          .getAgreements(_: String)(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementStatusEnum]
          )
        )
        .expects(
          Common.bearerToken,
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementManagementDependency.ACTIVE)
        )
        .returns(Future.successful(Seq(TestDataOne.agreement, TestDataTwo.agreement)))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataOne.eserviceId)
        .returns(Future.successful(TestDataOne.eService))
        .once()

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataTwo.eserviceId)
        .returns(Future.successful(TestDataTwo.eService))
        .once()

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: UUID))
        .expects(Common.bearerToken, UUID.fromString(Common.consumerId))
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.certifiedAttribute)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId1)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId2)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId2))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId3)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId3))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId1)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId1))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId2)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId2))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId3)
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
          .getAgreements(_: String)(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementStatusEnum]
          )
        )
        .expects(
          Common.bearerToken,
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementManagementDependency.ACTIVE)
        )
        .returns(Future.successful(Seq(TestDataOne.agreement, TestDataThree.agreement)))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataOne.eserviceId)
        .returns(Future.successful(TestDataOne.eService))
        .once()

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataThree.eserviceId)
        .returns(Future.successful(TestDataThree.eService))
        .once()

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: UUID))
        .expects(Common.bearerToken, UUID.fromString(Common.consumerId))
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.certifiedAttribute)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId1)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId1))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId2)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId2))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId3)
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
          .getAgreements(_: String)(
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[String],
            _: Option[AgreementManagementDependency.AgreementStatusEnum]
          )
        )
        .expects(
          Common.bearerToken,
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementManagementDependency.ACTIVE)
        )
        .returns(Future.successful(Seq(TestDataTwo.agreement, TestDataFour.agreement)))

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataTwo.eserviceId)
        .returns(Future.successful(TestDataTwo.eService))
        .once()

      (mockCatalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataFour.eserviceId)
        .returns(Future.successful(TestDataFour.eService))
        .once()

      (mockPartyManagementService
        .getPartyAttributes(_: String)(_: UUID))
        .expects(Common.bearerToken, UUID.fromString(Common.consumerId))
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.certifiedAttribute)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId1)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId1))

      (mockAttributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId3)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId3))

      val response =
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
