package it.pagopa.interop.agreementprocess

import it.pagopa.interop.commons.cqrs.model.ReadModelConfig
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementprocess.model.{
  AgreementPayload,
  AgreementRejectionPayload,
  AgreementSubmissionPayload,
  AgreementUpdatePayload
}
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.util.FakeDependencies._
import it.pagopa.interop.agreementprocess.util.{AuthorizedRoutes, AuthzScalatestRouteTest}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.cqrs.service.{MongoDbReadModelService, ReadModelService}
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.ExecutionContext

class AgreementApiAuthzSpec extends AnyWordSpecLike with MockFactory with AuthzScalatestRouteTest {

  val fakeAgreementManagementService: AgreementManagementService         = new FakeAgreementManagementService()
  val fakeCatalogManagementService: CatalogManagementService             = new FakeCatalogManagementService()
  val fakeTenantManagementService: TenantManagementService               = new FakeTenantManagementService()
  val fakeAttributeManagementService: AttributeManagementService         = new FakeAttributeManagementService()
  val fakeAuthorizationManagementService: AuthorizationManagementService = new FakeAuthorizationManagementService()
  val fakePartyProcessService: PartyProcessService                       = new FakePartyProcessService()
  val fakeUserRegistryService: UserRegistryService                       = new FakeUserRegistryService()
  val fakePDFCreator: PDFCreator                                         = new FakePDFCreator()
  val fakeFileManager: FileManager                                       = new FakeFileManager()
  val fakerQueueService: QueueService                                    = new FakeQueueService()

  implicit val fakeReadModel: ReadModelService = new MongoDbReadModelService(
    ReadModelConfig(
      "mongodb://localhost/?socketTimeoutMS=1&serverSelectionTimeoutMS=1&connectTimeoutMS=1&&autoReconnect=false&keepAlive=false",
      "db"
    )
  )

  val service: AgreementApiServiceImpl = AgreementApiServiceImpl(
    agreementManagementService = fakeAgreementManagementService,
    catalogManagementService = fakeCatalogManagementService,
    tenantManagementService = fakeTenantManagementService,
    attributeManagementService = fakeAttributeManagementService,
    authorizationManagementService = fakeAuthorizationManagementService,
    partyProcessService = fakePartyProcessService,
    userRegistry = fakeUserRegistryService,
    pdfCreator = fakePDFCreator,
    fileManager = fakeFileManager,
    offsetDateTimeSupplier = OffsetDateTimeSupplier,
    uuidSupplier = UUIDSupplier,
    fakerQueueService,
    fakerQueueService,
    fakerQueueService
  )(ExecutionContext.global, fakeReadModel)

  "Agreement api operation authorization spec" should {

    "accept authorized roles for createAgreement" in {
      val endpoint         = AuthorizedRoutes.endpoints("createAgreement")
      val agreementPayload = AgreementPayload(eserviceId = UUID.randomUUID(), descriptorId = UUID.randomUUID())

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.createAgreement(agreementPayload) }
      )
    }

    "accept authorized roles for deleteAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("deleteAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.deleteAgreement("fake") })
    }

    "accept authorized roles for submitAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("submitAgreement")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.submitAgreement("fake", AgreementSubmissionPayload(Some(""))) }
      )
    }

    "accept authorized roles for activateAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("activateAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.activateAgreement("fake") })
    }

    "accept authorized roles for rejectAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("rejectAgreement")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.rejectAgreement("fake", AgreementRejectionPayload("reason")) }
      )
    }

    "accept authorized roles for archiveAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("archiveAgreement")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.archiveAgreement(UUID.randomUUID().toString) }
      )
    }

    "accept authorized roles for suspendAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.suspendAgreement("fake") })
    }

    "accept authorized roles for getAgreements" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreements")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.getAgreements("eservicesIds", "consumersIds", "producersIds", "descriptorsIds", "states", 0, 0, false)
        }
      )
    }

    "accept authorized roles for getAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreementById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAgreementById("fake") })
    }

    "accept authorized roles for updateAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("updateAgreementById")
      val payload  = AgreementUpdatePayload("")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateAgreementById("fake", payload) }
      )
    }

    "accept authorized roles for upgradeAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("upgradeAgreementById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.upgradeAgreementById("fake") })
    }

    "accept authorized roles for computeAgreementsByAttribute" in {
      val endpoint = AuthorizedRoutes.endpoints("computeAgreementsByAttribute")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.computeAgreementsByAttribute(SpecData.computeAgreementStatePayload)
        }
      )
    }

    "accept authorized roles for getAgreementProducers" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreementProducers")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.getAgreementProducers(Some("query"), 0, 0)
        }
      )
    }

    "accept authorized roles for getAgreementConsumers" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreementConsumers")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.getAgreementConsumers(Some("query"), 0, 0)
        }
      )
    }
  }

  "accept authorized roles for getAgreementEServices" in {
    val endpoint = AuthorizedRoutes.endpoints("getAgreementEServices")
    validateAuthorization(
      endpoint,
      { implicit c: Seq[(String, String)] =>
        service.getAgreementEServices(Some("query"), "consumersIds", "producersIds", 0, 0)
      }
    )
  }
}
