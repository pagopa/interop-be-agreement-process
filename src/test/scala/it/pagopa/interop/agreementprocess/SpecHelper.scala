package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl}
import it.pagopa.interop.agreementprocess.api.{AgreementApiMarshaller, AgreementApiService}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{AgreementNotFound, EServiceNotFound}
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.util.PDFPayload
import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientComponentState
}
import it.pagopa.interop.catalogmanagement.model.CatalogItem
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentTenant
import it.pagopa.interop.commons.mail.TextMail
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, UID, USER_ROLES}
import it.pagopa.interop.selfcare.partyprocess.client.model.Institution
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource
import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementprocess.events.ArchiveEvent
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.scalamock.scalatest.MockFactory
import spray.json.JsonWriter

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait SpecHelper extends MockFactory {

  final lazy val url: String =
    s"http://localhost:8088/agreement-process/${buildinfo.BuildInfo.interfaceVersion}"

  val bearerToken: String  = "bearerToken"
  val requesterOrgId: UUID = UUID.randomUUID()

  implicit val contexts: Seq[(String, String)] =
    Seq(
      "bearer"              -> "bearerToken",
      ORGANIZATION_ID_CLAIM -> requesterOrgId.toString,
      USER_ROLES            -> "admin",
      UID                   -> SpecData.who.toString
    )

  val agreementApiMarshaller: AgreementApiMarshaller = AgreementApiMarshallerImpl

  val mockAgreementManagementService: AgreementManagementService         = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService             = mock[CatalogManagementService]
  val mockTenantManagementService: TenantManagementService               = mock[TenantManagementService]
  val mockAttributeManagementService: AttributeManagementService         = mock[AttributeManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService = mock[AuthorizationManagementService]
  val mockUserRegistryService: UserRegistryService                       = mock[UserRegistryService]
  val mockPartyProcessService: PartyProcessService                       = mock[PartyProcessService]
  val mockPDFCreator: PDFCreator                                         = mock[PDFCreator]
  val mockFileManager: FileManager                                       = mock[FileManager]
  val mockOffsetDateTimeSupplier: OffsetDateTimeSupplier                 = () => SpecData.when
  val mockUUIDSupplier: UUIDSupplier                                     = () => UUID.randomUUID()
  val mockQueueService: QueueService                                     = mock[QueueService]
  val mockReadModel: ReadModelService                                    = mock[ReadModelService]

  val service: AgreementApiService = AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockTenantManagementService,
    mockAttributeManagementService,
    mockAuthorizationManagementService,
    mockPartyProcessService,
    mockUserRegistryService,
    mockPDFCreator,
    mockFileManager,
    mockOffsetDateTimeSupplier,
    mockUUIDSupplier,
    mockQueueService,
    mockQueueService,
    mockQueueService
  )(ExecutionContext.global, mockReadModel)

  def contextWithRole(role: String): Seq[(String, String)] = contexts.filter { case (key, _) =>
    key != USER_ROLES
  } :+ (USER_ROLES -> role)

  def mockEServiceRetrieve(eServiceId: UUID, result: CatalogItem) =
    (mockCatalogManagementService
      .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(eServiceId, *, *)
      .once()
      .returns(Future.successful(result))

  def mockEServiceRetrieveNotFound(eServiceId: UUID) =
    (mockCatalogManagementService
      .getEServiceById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(eServiceId, *, *)
      .once()
      .returns(Future.failed(EServiceNotFound(eServiceId)))

  def mockAgreementCreation(result: Agreement) =
    (mockAgreementManagementService
      .createAgreement(_: AgreementSeed)(_: Seq[(String, String)]))
      .expects(*, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementDeletion(agreementId: UUID) =
    (mockAgreementManagementService
      .deleteAgreement(_: UUID)(_: Seq[(String, String)]))
      .expects(agreementId, *)
      .once()
      .returns(Future.unit)

  def mockAgreementUpdate(agreementId: UUID, seed: UpdateAgreementSeed, result: Agreement) =
    (mockAgreementManagementService
      .updateAgreement(_: UUID, _: UpdateAgreementSeed)(_: Seq[(String, String)]))
      .expects(agreementId, seed, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementUpdateIgnoreSeed(agreementId: UUID) =
    (mockAgreementManagementService
      .updateAgreement(_: UUID, _: UpdateAgreementSeed)(_: Seq[(String, String)]))
      .expects(agreementId, *, *)
      .once()
      .returns(Future.successful(SpecData.agreement))

  def mockAgreementRetrieve(result: PersistentAgreement) =
    (mockAgreementManagementService
      .getAgreementById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(*, *, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementRetrieveNotFound(agreementId: UUID) =
    (mockAgreementManagementService
      .getAgreementById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(*, *, *)
      .once()
      .returns(Future.failed(AgreementNotFound(agreementId.toString)))

  def mockAgreementsRetrieve(result: Seq[PersistentAgreement]) =
    (mockAgreementManagementService
      .getAgreements(
        _: Option[UUID],
        _: Option[UUID],
        _: Option[UUID],
        _: Option[UUID],
        _: List[PersistentAgreementState],
        _: Option[UUID]
      )(_: ExecutionContext, _: ReadModelService))
      .expects(*, *, *, *, *, *, *, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementUpgrade(agreementId: UUID, seed: UpgradeAgreementSeed, result: Agreement) =
    (mockAgreementManagementService
      .upgradeById(_: UUID, _: UpgradeAgreementSeed)(_: Seq[(String, String)]))
      .expects(agreementId, seed, *)
      .once()
      .returns(Future.successful(result))

  def mockTenantRetrieve(tenantId: UUID, result: PersistentTenant) =
    (mockTenantManagementService
      .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(tenantId, *, *)
      .once()
      .returns(Future.successful(result))

  def mockTenantRetrieveNotFound(tenantId: UUID, result: PersistentTenant) =
    (mockTenantManagementService
      .getTenantById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(tenantId, *, *)
      .once()
      .returns(Future.successful(result))

  def mockClientStateUpdate(eServiceId: UUID, consumerId: UUID, agreementId: UUID, state: ClientComponentState) =
    (mockAuthorizationManagementService
      .updateStateOnClients(_: UUID, _: UUID, _: UUID, _: ClientComponentState)(_: Seq[(String, String)]))
      .expects(eServiceId, consumerId, agreementId, state, *)
      .once()
      .returns(Future.unit)

  def mockUpdateAgreementAndEServiceStates(
    eServiceId: UUID,
    consumerId: UUID,
    payload: ClientAgreementAndEServiceDetailsUpdate
  ) =
    (mockAuthorizationManagementService
      .updateAgreementAndEServiceStates(_: UUID, _: UUID, _: ClientAgreementAndEServiceDetailsUpdate)(
        _: Seq[(String, String)]
      ))
      .expects(eServiceId, consumerId, payload, *)
      .once()
      .returns(Future.unit)

  def mockUserRegistryRetrieve(user: UserResource) =
    (mockUserRegistryService
      .getUserById(_: UUID)(_: Seq[(String, String)]))
      .expects(*, *)
      .once()
      .returns(Future.successful(user))

  def mockAttributeManagementServiceRetrieve(attribute: ClientAttribute) =
    (mockAttributeManagementService
      .getAttributeById(_: UUID)(_: ExecutionContext, _: ReadModelService))
      .expects(*, *, *)
      .returns(Future.successful(attribute))

  def mockPDFCreatorCreate =
    (mockPDFCreator.create(_: String, _: PDFPayload)).expects(*, *).returns(Future.successful(Array.empty))

  def mockFileManagerWrite =
    (mockFileManager
      .storeBytes(_: String, _: String)(_: String, _: String, _: Array[Byte]))
      .expects(*, *, *, *, *)
      .returns(Future.successful("path"))

  def mockEnvelopeSending =
    (mockQueueService
      .send[TextMail](_: TextMail)(_: JsonWriter[TextMail]))
      .expects(*, *)
      .returns(Future.successful("sent"))

  def mockArchiveEventSending(event: ArchiveEvent) =
    (mockQueueService
      .send[ArchiveEvent](_: ArchiveEvent)(_: JsonWriter[ArchiveEvent]))
      .expects(event, *)
      .returns(Future.successful("sent"))

  def mockGetInstitution(selfcareId: String) =
    (mockPartyProcessService
      .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
      .expects(*, *, *)
      .returns(
        Future.successful(
          Institution(
            id = UUID.fromString(selfcareId),
            externalId = "externalId",
            originId = "originId",
            description = "description",
            digitalAddress = "digitalAddress",
            address = "address",
            zipCode = "zipCode",
            taxCode = "taxCode",
            origin = "origin",
            institutionType = None,
            attributes = Seq.empty
          )
        )
      )

  def mockAddConsumerDocument =
    (mockAgreementManagementService
      .addConsumerDocument(_: UUID, _: DocumentSeed)(_: Seq[(String, String)]))
      .expects(*, *, *)
      .returns(Future.successful(SpecData.document(UUID.randomUUID())))

  def mockAgreementContract =
    (mockAgreementManagementService
      .addAgreementContract(_: UUID, _: DocumentSeed)(_: Seq[(String, String)]))
      .expects(*, *, *)
      .returns(Future.successful(SpecData.document(UUID.randomUUID())))

  def mockFileDeletion(containerPath: String, filePath: String) =
    (mockFileManager
      .delete(_: String)(_: String))
      .expects(containerPath, filePath)
      .once()
      .returns(Future.successful(true))

  def mockFileCopy =
    (mockFileManager
      .copy(_: String, _: String)(_: String, _: String, _: String))
      .expects(*, *, *, *, *)
      .once()
      .returns(Future.successful("path"))

  def mockContractCreation(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    consumer: PersistentTenant,
    expectedSeed: UpdateAgreementSeed
  ) = {
    val producer = SpecData.tenant.copy(id = agreement.producerId, selfcareId = Some(UUID.randomUUID().toString))

    mockAgreementRetrieve(agreement)
    mockAgreementsRetrieve(Nil)
    mockEServiceRetrieve(eService.id, eService)
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockPDFCreatorCreate
    mockFileManagerWrite
    mockAgreementContract
    mockTenantRetrieve(consumer.id, consumer)
    mockTenantRetrieve(producer.id, producer)
    mockUserRegistryRetrieve(SpecData.userResource("a", "b", "c"))
    mockUserRegistryRetrieve(SpecData.userResource("d", "e", "f"))
    mockAgreementUpdate(
      agreement.id,
      expectedSeed,
      agreement.copy(state = expectedSeed.state.toPersistent, stamps = expectedSeed.stamps.toPersistent).toManagement
    )
    mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)
    mockGetInstitution(consumer.selfcareId.get)
    mockGetInstitution(producer.selfcareId.get)
    mockEnvelopeSending
  }

  def mockAutomaticActivation(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    consumer: PersistentTenant,
    producer: PersistentTenant,
    expectedSeed: UpdateAgreementSeed
  ) = {

    mockAgreementRetrieve(agreement)
    mockAgreementsRetrieve(Nil)
    mockEServiceRetrieve(eService.id, eService)
    mockAgreementsRetrieve(Nil)
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockTenantRetrieve(consumer.id, consumer)
    mockTenantRetrieve(agreement.consumerId, consumer)
    mockAgreementUpdate(
      agreement.id,
      expectedSeed,
      agreement
        .copy(state = expectedSeed.state.toPersistent, stamps = SpecData.activationStamps.toPersistent)
        .toManagement
    )
    mockPDFCreatorCreate
    mockFileManagerWrite
    mockAgreementContract
    mockTenantRetrieve(producer.id, producer)
    mockUserRegistryRetrieve(SpecData.userResource("a", "b", "c"))
    mockUserRegistryRetrieve(SpecData.userResource("d", "e", "f"))
    mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)
    mockGetInstitution(consumer.selfcareId.get)
    mockGetInstitution(producer.selfcareId.get)
    mockEnvelopeSending
  }

  def mockSelfActivation(
    agreement: PersistentAgreement,
    eService: CatalogItem,
    consumer: PersistentTenant,
    producer: PersistentTenant,
    expectedSeed: UpdateAgreementSeed
  ) = {

    mockAgreementRetrieve(agreement)
    mockAgreementsRetrieve(Nil)
    mockAgreementsRetrieve(Nil)
    mockEServiceRetrieve(eService.id, eService)
    mockTenantRetrieve(consumer.id, consumer)
    mockTenantRetrieve(agreement.consumerId, consumer)
    mockTenantRetrieve(producer.id, producer)
    mockAgreementUpdate(
      agreement.id,
      expectedSeed,
      agreement
        .copy(state = expectedSeed.state.toPersistent, stamps = SpecData.activationStamps.toPersistent)
        .toManagement
    )
    mockPDFCreatorCreate
    mockFileManagerWrite
    mockAgreementContract
    mockUserRegistryRetrieve(SpecData.userResource("a", "b", "c"))
    mockUserRegistryRetrieve(SpecData.userResource("d", "e", "f"))
    mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)
    mockGetInstitution(consumer.selfcareId.get)
    mockGetInstitution(producer.selfcareId.get)
    mockEnvelopeSending
  }
}
