package it.pagopa.interop.agreementprocess.contract.catalog

import com.itv.scalapact.model.ScalaPactDescription
import com.itv.scalapact.{ScalaPactMockConfig, ScalaPactMockServer}
import it.pagopa.interop.catalogmanagement
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.Serializers
import it.pagopa.interop.catalogmanagement.client.model._
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl.CatalogManagementServiceImpl
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import scala.concurrent.ExecutionContext

/** Tests the integration with Catalog Management service, creating a corresponding pact interaction file */
class CatalogProviderContractSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  // The import contains two things:
  // 1. The consumer test DSL/Builder
  // 2. Helper implicits, for instance, values will automatically be converted
  //    to Option types where the DSL requires it.
  import com.itv.scalapact.ScalaPactForger._
  // Import the json and http libraries specified in the build.sbt file
  import com.itv.scalapact.http._
  import com.itv.scalapact.json._

  // defining json4s serialization formats for Catalog Management data types
  implicit val formats: Formats = DefaultFormats ++ Serializers.all ++ catalogmanagement.client.api.EnumsSerializers.all

  // defining names of the interactions
  val CONSUMER = "agreement-process-consumer"
  val PROVIDER = "catalog-management-provider"

  // given the following mock request payload...
  val eserviceId = "24772a3d-e6f2-47f2-96e5-4cbd1e4e8c84"

  val mockDescriptor = EServiceDescriptor(
    id = UUID.fromString("24772a3d-e6f2-47f2-96e5-4cbd1e4e9999"),
    version = "1",
    description = None,
    interface = None,
    docs = Seq.empty,
    audience = Seq("pippo"),
    voucherLifespan = 124,
    state = EServiceDescriptorState.DRAFT,
    dailyCallsTotal = 1000,
    dailyCallsPerConsumer = 1000
  )

  val response = EService(
    id = UUID.fromString(eserviceId),
    producerId = UUID.fromString("24772a3d-e6f2-47f2-96e5-4cbd1e4e9999"),
    name = "string",
    description = "string",
    technology = EServiceTechnology.REST,
    attributes = Attributes(
      declared = Seq(Attribute(single = Some(AttributeValue("1234", false)))),
      certified = Seq(Attribute(single = Some(AttributeValue("1234", false)))),
      verified = Seq(Attribute(group = Some(Seq(AttributeValue("1234", false), AttributeValue("5555", false)))))
    ),
    descriptors = Seq(mockDescriptor)
  )

  val objectBody = Serialization.write(response)(formats)

  // Forge all pacts up front
  val pact: ScalaPactDescription = forgePact
    .between(CONSUMER)
    .and(PROVIDER)
    .addInteraction(
      interaction
        .description("Fetching e-service by id")
        .provided("e-service id")
        .uponReceiving(
          method = GET,
          path = s"/catalog-management/0.0.1/eservices/$eserviceId",
          query = None,
          headers = Map("Authorization" -> "Bearer 1234")
        )
        .willRespondWith(
          status = 200,
          headers = Map("Content-Type" -> "application/json"),
          body = Some(objectBody),
          matchingRules = bodyTypeRule("name") // service name should be a string
            ~> bodyTypeRule("description")     // description should be a string
            ~> bodyTypeRule("id")
            ~> bodyArrayMinimumLengthRule("descriptors", 1)
            ~> bodyRegexRule("descriptors[*].status", "draft|active") // status should be either draft or active
        )
    )

  lazy val server: ScalaPactMockServer = pact.startServer()
  lazy val config: ScalaPactMockConfig = server.config

  override def beforeAll(): Unit = {
    // Initialize the Pact stub server prior to tests executing.
    val _ = server
    ()
  }

  override def afterAll(): Unit = {
    // Shut down the stub server when tests are finished.
    server.stop()
  }

  // it launches a mock server and tests the interaction and the expected outcome
  "Connecting to Catalog Management service" should {
    "be able to get the eservice by id" in {
      val catalogManagementInvoker = CatalogManagementInvoker(ExecutionContext.global)(system.classicSystem)
      def catalogManagement(catalogApi: EServiceApi): CatalogManagementService =
        CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)(system.executionContext)

      val results = catalogManagement(EServiceApi(s"${config.baseUrl}/catalog-management/0.0.1"))
        .getEServiceById(UUID.fromString(eserviceId))(Seq("bearer" -> "1234"))
      val value   = results.futureValue
      value.producerId.toString shouldBe "24772a3d-e6f2-47f2-96e5-4cbd1e4e9999"
      value.descriptors(0).audience(0) shouldBe "pippo"
    }
  }
}
