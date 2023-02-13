package it.pagopa.interop.agreementprocess.util

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.util.PDFPayload
import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributeKind
import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.catalogmanagement.client.model.{Attributes, EService, EServiceTechnology}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.selfcare.partyprocess.client.model.Institution
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource
import it.pagopa.interop.tenantmanagement.client.model.{ExternalId, Tenant}
import spray.json.JsonWriter

import java.io.{ByteArrayOutputStream, File}
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeAttributeManagementService extends AttributeManagementService {
    val attribute: ClientAttribute = attributeregistrymanagement.client.model.Attribute(
      id = UUID.randomUUID(),
      kind = AttributeKind.DECLARED,
      description = "fake",
      name = "fake",
      creationTime = OffsetDateTime.now()
    )

    override def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
      Future.successful(attribute)

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

    override def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
      Future.successful(agreement)

    override def getAgreements(
      producerId: Option[String],
      consumerId: Option[String],
      eserviceId: Option[String],
      descriptorId: Option[String],
      states: List[AgreementState],
      attributeId: Option[String] = None
    )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = Future.successful(Seq.empty)

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
      contexts: Seq[(String, String)]
    ): Future[Document] = Future.successful(document)

    override def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
      contexts: Seq[(String, String)]
    ): Future[Unit] = Future.unit
  }

  class FakeCatalogManagementService       extends CatalogManagementService       {
    override def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
      Future.successful(
        EService(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          name = "fake",
          description = "fake",
          technology = EServiceTechnology.REST,
          attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
          descriptors = Seq.empty
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
    override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
      Future.successful(
        Tenant(
          id = UUID.randomUUID(),
          selfcareId = Some(UUID.randomUUID().toString),
          externalId = ExternalId("origin", "value"),
          features = Nil,
          attributes = Nil,
          createdAt = OffsetDateTime.now(),
          updatedAt = None,
          mails = Nil,
          name = "test_name"
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
  }

  class FakeQueueService extends QueueService {
    override def send[T: JsonWriter](message: T): Future[String] = Future.successful("Sent")
  }

}
