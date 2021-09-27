package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementEnums
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{AgreementApiServiceImpl, ConsumerApiMarshallerImpl}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.{AgreementApi, ConsumerApiMarshaller, HealthApi}
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

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AgreementApiServiceSpec extends AnyWordSpecLike with MockFactory with SpecHelper with ScalatestRouteTest {

  val consumerApiMarshaller: ConsumerApiMarshaller               = new ConsumerApiMarshallerImpl
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
        .activateById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(pendingAgreement))

      Get() ~> service.activateAgreement(TestDataOne.id.toString) ~> check {
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
        .activateById(_: String)(_: String))
        .expects(Common.bearerToken, TestDataOne.id.toString)
        .once()
        .returns(Future.successful(suspendedAgreement))

      Get() ~> service.activateAgreement(TestDataOne.id.toString) ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "fail if missing authorization header" in {
      val contexts: Seq[(String, String)] = Seq.empty[(String, String)]
      Get() ~> service.activateAgreement(TestDataOne.id.toString)(toEntityMarshallerProblem, contexts) ~> check {
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

      Get() ~> service.activateAgreement(TestDataOne.id.toString) ~> check {
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

      Get() ~> service.activateAgreement(TestDataOne.id.toString) ~> check {
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

      Get() ~> service.activateAgreement(TestDataOne.id.toString) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

  }

}
