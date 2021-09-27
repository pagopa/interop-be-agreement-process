package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementEnums
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  localTimeFormat,
  uuidFormat
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.{
  AgreementApi,
  AgreementApiMarshaller,
  ConsumerApi,
  HealthApi
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.common.system.{Authenticator, executionContext}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{
  Agreement,
  AgreementPayload,
  Attribute,
  Attributes,
  EService,
  Organization
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementprocess.service._
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.{DefaultJsonProtocol, enrichAny}

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class AgreementApiServiceSpec
    extends ScalaTestWithActorTestKit
    with MockFactory
    with AnyWordSpecLike
    with SprayJsonSupport
    with DefaultJsonProtocol
    with SpecHelper {

  implicit val testSystem: ActorSystem = system.classicSystem

  val agreementApiMarshaller: AgreementApiMarshaller         = new AgreementApiMarshallerImpl
  val mockHealthApi: HealthApi                               = mock[HealthApi]
  val mockConsumerApi: ConsumerApi                           = mock[ConsumerApi]
  val partyManagementService: PartyManagementService         = mock[PartyManagementService]
  val agreementManagementService: AgreementManagementService = mock[AgreementManagementService]
  val catalogManagementService: CatalogManagementService     = mock[CatalogManagementService]
  val attributeManagementService: AttributeManagementService = mock[AttributeManagementService]

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  override def beforeAll(): Unit = {

    val agreementApi = new AgreementApi(
      new AgreementApiServiceImpl(
        agreementManagementService = agreementManagementService,
        partyManagementService = partyManagementService,
        catalogManagementService = catalogManagementService,
        attributeManagementService = attributeManagementService
      ),
      agreementApiMarshaller,
      wrappingDirective
    )

    controller = Some(new Controller(agreement = agreementApi, health = mockHealthApi, consumer = mockConsumerApi))

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

  "Processing an agreement request" should {

    "retrieve all attributes owned by a customer (customer with all kind attributes)" in {

      (agreementManagementService
        .getAgreements(_: String)(
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String],
          _: Option[String]
        ))
        .expects(
          Common.bearerToken,
          None,
          Some(Common.consumerId),
          None,
          None,
          Some(AgreementEnums.Status.Active.toString)
        )
        .returns(Future.successful(Seq(TestDataOne.agreement, TestDataTwo.agreement)))

      (catalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataOne.eserviceId)
        .returns(Future.successful(TestDataOne.eService))
        .once()

      (catalogManagementService
        .getEServiceById(_: String)(_: UUID))
        .expects(Common.bearerToken, TestDataTwo.eserviceId)
        .returns(Future.successful(TestDataTwo.eService))
        .once()

      (partyManagementService
        .getPartyAttributes(_: String)(_: String))
        .expects(Common.bearerToken, Common.consumerId)
        .returns(Future.successful(Seq(Common.certifiedAttribute)))

      (attributeManagementService.getAttribute _)
        .expects(Common.certifiedAttribute)
        .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

      (attributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId1)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId1))

      (attributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId2)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId2))

      (attributeManagementService.getAttribute _)
        .expects(Common.verifiedAttributeId3)
        .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId3))

      (attributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId1)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId1))

      (attributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId2)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId2))

      (attributeManagementService.getAttribute _)
        .expects(Common.declaredAttributeId3)
        .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId3))

      import akka.actor.ActorSystem
      import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
      import akka.http.scaladsl.Http
      import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
      import akka.http.scaladsl.model.HttpMethods
      import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
      import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
      import org.scalamock.scalatest.MockFactory
      import org.scalatest.wordspec.AnyWordSpecLike
      import spray.json.{DefaultJsonProtocol, RootJsonFormat}

      import java.util.UUID
      import scala.concurrent.duration.{Duration, DurationInt}
      import scala.concurrent.{Await, Future}

      class AgreementApiServiceSpec
          extends ScalaTestWithActorTestKit
          with MockFactory
          with AnyWordSpecLike
          with SprayJsonSupport
          with DefaultJsonProtocol
          with SpecHelper {

        implicit val testSystem: ActorSystem = system.classicSystem

        val agreementApiMarshaller: AgreementApiMarshaller         = new AgreementApiMarshallerImpl
        val mockHealthApi: HealthApi                               = mock[HealthApi]
        val mockConsumerApi: ConsumerApi                           = mock[ConsumerApi]
        val partyManagementService: PartyManagementService         = mock[PartyManagementService]
        val agreementManagementService: AgreementManagementService = mock[AgreementManagementService]
        val catalogManagementService: CatalogManagementService     = mock[CatalogManagementService]
        val attributeManagementService: AttributeManagementService = mock[AttributeManagementService]

        var controller: Option[Controller]                 = None
        var bindServer: Option[Future[Http.ServerBinding]] = None

        val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
          SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

        override def beforeAll(): Unit = {

          val agreementApi = new AgreementApi(
            new AgreementApiServiceImpl(
              agreementManagementService = agreementManagementService,
              partyManagementService = partyManagementService,
              catalogManagementService = catalogManagementService,
              attributeManagementService = attributeManagementService
            ),
            agreementApiMarshaller,
            wrappingDirective
          )

          controller =
            Some(new Controller(agreement = agreementApi, health = mockHealthApi, consumer = mockConsumerApi))

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

        "Processing an agreement request" should {

          "retrieve all attributes owned by a customer (customer with all kind attributes)" in {

            (agreementManagementService
              .getAgreements(_: String)(
                _: Option[String],
                _: Option[String],
                _: Option[String],
                _: Option[String],
                _: Option[String]
              ))
              .expects(
                Common.bearerToken,
                None,
                Some(Common.consumerId),
                None,
                None,
                Some(AgreementEnums.Status.Active.toString)
              )
              .returns(Future.successful(Seq(TestDataOne.agreement, TestDataTwo.agreement)))

            (catalogManagementService
              .getEServiceById(_: String)(_: UUID))
              .expects(Common.bearerToken, TestDataOne.eserviceId)
              .returns(Future.successful(TestDataOne.eService))
              .once()

            (catalogManagementService
              .getEServiceById(_: String)(_: UUID))
              .expects(Common.bearerToken, TestDataTwo.eserviceId)
              .returns(Future.successful(TestDataTwo.eService))
              .once()

            (partyManagementService
              .getPartyAttributes(_: String)(_: String))
              .expects(Common.bearerToken, Common.consumerId)
              .returns(Future.successful(Seq(Common.certifiedAttribute)))

            (attributeManagementService.getAttribute _)
              .expects(Common.certifiedAttribute)
              .returns(Future.successful[ClientAttribute](ClientAttributes.certifiedAttribute))

            (attributeManagementService.getAttribute _)
              .expects(Common.verifiedAttributeId1)
              .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId1))

            (attributeManagementService.getAttribute _)
              .expects(Common.verifiedAttributeId2)
              .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId2))

            (attributeManagementService.getAttribute _)
              .expects(Common.verifiedAttributeId3)
              .returns(Future.successful[ClientAttribute](ClientAttributes.verifiedAttributeId3))

            (attributeManagementService.getAttribute _)
              .expects(Common.declaredAttributeId1)
              .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId1))

            (attributeManagementService.getAttribute _)
              .expects(Common.declaredAttributeId2)
              .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId2))

            (attributeManagementService.getAttribute _)
              .expects(Common.declaredAttributeId3)
              .returns(Future.successful[ClientAttribute](ClientAttributes.declaredAttributeId3))

            val payload = AgreementPayload(
              eserviceId = TestDataSix.eserviceId,
              descriptorId = TestDataSix.descriptorId,
              consumerId = TestDataSix.consumerId
            )

            implicit val agreementPayloadFormat: RootJsonFormat[AgreementPayload] =
              jsonFormat3(AgreementPayload)

            val requestData = Source.single(ByteString.fromString(payload.toJson.compactPrint))

            val response = request(data = requestData, path = "agreements", verb = HttpMethods.POST)

            implicit def organizationJsonFormat: RootJsonFormat[Organization] = jsonFormat2(Organization)
            implicit def eServiceJsonFormat: RootJsonFormat[EService]         = jsonFormat3(EService)
            implicit def attributeJsonFormat: RootJsonFormat[Attribute]       = jsonFormat9(Attribute)
            implicit def agreementJsonFormat: RootJsonFormat[Agreement]       = jsonFormat6(Agreement)

            implicit val fromEntityUnmarshallerAttributes: FromEntityUnmarshaller[Agreement] =
              sprayJsonUnmarshaller[Agreement]

            val body = Await.result(Unmarshal(response.entity).to[Agreement], Duration.Inf)

          }

        }

      }

    }

  }

}
