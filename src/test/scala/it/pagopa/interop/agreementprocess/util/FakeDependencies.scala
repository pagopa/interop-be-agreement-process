package it.pagopa.interop.agreementprocess.util

import cats.syntax.all._
import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.util.PDFPayload
import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.selfcare.partyprocess.client.model.Institution
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource
import spray.json.JsonWriter
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.model.agreement.{
  PersistentAgreementDocument,
  PersistentAgreement,
  PersistentAgreementState
}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.{PersistentAttribute, Declared}
import it.pagopa.interop.catalogmanagement.model.{CatalogItem, Rest, CatalogAttributes}
import it.pagopa.interop.tenantmanagement.model.tenant.{PersistentTenant, PersistentExternalId, PersistentTenantKind}
import it.pagopa.interop.catalogmanagement.model.{CatalogDescriptor, Published, Automatic, Deliver}

import java.io.{ByteArrayOutputStream, File}
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeAttributeManagementService extends AttributeManagementService {
    val attribute: PersistentAttribute = PersistentAttribute(
      id = UUID.randomUUID(),
      code = "code".some,
      origin = "origin".some,
      kind = Declared,
      description = "fake",
      name = "fake",
      creationTime = OffsetDateTime.now()
    )

    override def getAttributeById(
      attributeId: UUID
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAttribute] =
      Future.successful(attribute.copy(id = attributeId))

  }

  class FakeAgreementManagementService extends AgreementManagementService {
    val agreement: Agreement = Agreement(
      id = UUID.randomUUID(),
      eserviceId = UUID.randomUUID(),
      descriptorId = UUID.randomUUID(),
      producerId = UUID.randomUUID(),
      consumerId = UUID.randomUUID(),
      state = AgreementState.ACTIVE,
      certifiedAttributes = Nil,
      declaredAttributes = Nil,
      verifiedAttributes = Nil,
      consumerDocuments = Nil,
      createdAt = OffsetDateTime.now(),
      stamps = Stamps()
    )

    val document: Document = Document(
      id = UUID.randomUUID(),
      name = "name",
      prettyName = "prettyName",
      contentType = "contentType",
      path = "path",
      createdAt = OffsetDateTime.now()
    )

    override def createAgreement(seed: AgreementSeed)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
      Future.successful(agreement)

    override def getAgreementById(
      agreementId: UUID
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentAgreement] =
      Future.successful(agreement.toPersistent)

    override def getAgreements(
      producerId: Option[UUID],
      consumerId: Option[UUID],
      eserviceId: Option[UUID],
      descriptorId: Option[UUID],
      states: Seq[PersistentAgreementState],
      attributeId: Option[UUID]
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] =
      Future.successful(Seq.empty)

    override def upgradeById(agreementId: UUID, agreementSeed: UpgradeAgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)

    override def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)

    override def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
      Future.unit

    override def addAgreementContract(agreementId: UUID, seed: DocumentSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Document] = Future.successful(document)

    override def addConsumerDocument(agreementId: UUID, seed: DocumentSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Document] = Future.successful(document)

    override def getConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
      ec: ExecutionContext,
      readModel: ReadModelService
    ): Future[PersistentAgreementDocument] = Future.successful(document.toPersistent)

    override def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.unit
  }

  class FakeCatalogManagementService       extends CatalogManagementService       {
    override def getEServiceById(
      eServiceId: UUID
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[CatalogItem] =
      Future.successful(
        CatalogItem(
          id = eServiceId,
          producerId = UUID.randomUUID(),
          name = "fake",
          description = "fake",
          technology = Rest,
          descriptors = CatalogDescriptor(
            id = UUID.randomUUID(),
            version = "1",
            audience = Nil,
            voucherLifespan = 0,
            dailyCallsPerConsumer = 0,
            dailyCallsTotal = 0,
            docs = Nil,
            state = Published,
            agreementApprovalPolicy = Automatic.some,
            serverUrls = Nil,
            attributes = CatalogAttributes(Nil, Nil, Nil),
            description = None,
            interface = None,
            createdAt = OffsetDateTime.now(),
            publishedAt = OffsetDateTime.now().some,
            suspendedAt = None,
            deprecatedAt = None,
            archivedAt = None
          ) :: Nil,
          attributes = None,
          createdAt = OffsetDateTime.now(),
          riskAnalysis = Seq.empty,
          mode = Deliver
        )
      )
  }
  class FakeAuthorizationManagementService extends AuthorizationManagementService {
    override def updateStateOnClients(
      eServiceId: UUID,
      consumerId: UUID,
      agreementId: UUID,
      state: ClientComponentState
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.unit

    override def updateAgreementAndEServiceStates(
      eServiceId: UUID,
      consumerId: UUID,
      payload: ClientAgreementAndEServiceDetailsUpdate
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.unit
  }

  class FakeTenantManagementService extends TenantManagementService {
    override def getTenantById(
      tenantId: UUID
    )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PersistentTenant] =
      Future.successful(
        PersistentTenant(
          id = UUID.randomUUID(),
          selfcareId = Some(UUID.randomUUID().toString),
          externalId = PersistentExternalId("origin", "value"),
          features = Nil,
          attributes = Nil,
          createdAt = OffsetDateTime.now(),
          updatedAt = None,
          mails = Nil,
          name = "test_name",
          kind = PersistentTenantKind.PA.some
        )
      )
  }

  class FakePartyProcessService extends PartyProcessService {
    override def getInstitution(
      selfcareId: String
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = Future.successful(
      Institution(
        id = UUID.randomUUID(),
        externalId = "",
        originId = "",
        description = "",
        digitalAddress = "",
        address = "",
        zipCode = "",
        taxCode = "",
        origin = "",
        institutionType = None,
        attributes = Seq.empty
      )
    )
  }

  class FakeUserRegistryService extends UserRegistryService {
    override def getUserById(id: UUID)(implicit contexts: Seq[(String, String)]): Future[UserResource] =
      Future.successful(
        UserResource(
          birthDate = None,
          email = None,
          familyName = None,
          fiscalCode = None,
          id = UUID.randomUUID(),
          name = None,
          workContacts = None
        )
      )
  }

  class FakePDFCreator extends PDFCreator {
    override def create(template: String, pdfPayload: PDFPayload): Future[Array[Byte]] = Future.successful(Array.empty)
  }

  class FakeFileManager extends FileManager {

    override def close(): Unit = ()

    override def store(containerPath: String, path: String)(
      resourceId: String,
      fileParts: (FileInfo, File)
    ): Future[StorageFilePath] = Future.successful("")

    override def storeBytes(
      containerPath: String,
      path: String
    )(resourceId: String, fileName: String, fileContent: Array[Byte]): Future[StorageFilePath] = Future.successful("")

    override def copy(
      containerPath: String,
      path: String
    )(filePathToCopy: String, resourceId: String, fileName: String): Future[StorageFilePath] = Future.successful("")

    override def get(containerPath: String)(filePath: StorageFilePath): Future[ByteArrayOutputStream] =
      Future.successful(new ByteArrayOutputStream())

    override def delete(containerPath: String)(filePath: StorageFilePath): Future[Boolean] = Future.successful(true)

    override def storeBytes(containerPath: String, path: String, filename: String)(
      fileContent: Array[Byte]
    ): Future[StorageFilePath] = Future.successful("")

    override def listFiles(container: String)(path: String): Future[List[StorageFilePath]] = Future.successful(Nil)

    override def getFile(container: String)(path: String): Future[Array[Byte]] = Future.successful(Array.empty)

    override def getAllFiles(container: String)(path: String): Future[Map[String, Array[Byte]]] =
      Future.successful(Map.empty)
  }

  class FakeQueueService extends QueueService {
    override def send[T: JsonWriter](message: T): Future[String] = Future.successful("Sent")
  }

}
