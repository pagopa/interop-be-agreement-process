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
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, UID, USER_ROLES}
import it.pagopa.interop.selfcare.partymanagement.client.model.Institution
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import org.scalamock.scalatest.MockFactory

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
  val mockPartyManagementService: PartyManagementService                 = mock[PartyManagementService]
  val mockUserRegistryService: UserRegistryService                       = mock[UserRegistryService]
  val mockPDFCreator: PDFCreator                                         = mock[PDFCreator]
  val mockFileManager: FileManager                                       = mock[FileManager]
  val mockOffsetDateTimeSupplier: OffsetDateTimeSupplier                 = () => SpecData.when
  val mockUUIDSupplier: UUIDSupplier                                     = () => UUID.randomUUID()

  val service: AgreementApiService = AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockTenantManagementService,
    mockAttributeManagementService,
    mockAuthorizationManagementService,
    mockPartyManagementService,
    mockUserRegistryService,
    mockPDFCreator,
    mockFileManager,
    mockOffsetDateTimeSupplier,
    mockUUIDSupplier
  )(ExecutionContext.global)

  def contextWithRole(role: String): Seq[(String, String)] = contexts.filter { case (key, _) =>
    key != USER_ROLES
  } :+ (USER_ROLES -> role)

  def mockEServiceRetrieve(eServiceId: UUID, result: EService) =
    (mockCatalogManagementService
      .getEServiceById(_: UUID)(_: Seq[(String, String)]))
      .expects(eServiceId, *)
      .once()
      .returns(Future.successful(result))

  def mockEServiceRetrieveNotFound(eServiceId: UUID) =
    (mockCatalogManagementService
      .getEServiceById(_: UUID)(_: Seq[(String, String)]))
      .expects(eServiceId, *)
      .once()
      .returns(Future.failed(EServiceNotFound(eServiceId)))

  def mockAgreementCreation(result: Agreement) =
    (mockAgreementManagementService
      .createAgreement(_: UUID, _: UUID, _: UUID, _: UUID)(_: Seq[(String, String)]))
      .expects(*, *, *, *, *)
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
      .expects(agreementId, seed, contexts)
      .once()
      .returns(Future.successful(result))

  def mockAgreementUpdateIgnoreSeed(agreementId: UUID) =
    (mockAgreementManagementService
      .updateAgreement(_: UUID, _: UpdateAgreementSeed)(_: Seq[(String, String)]))
      .expects(agreementId, *, *)
      .once()
      .returns(Future.successful(SpecData.agreement))

  def mockAgreementRetrieve(result: Agreement) =
    (mockAgreementManagementService
      .getAgreementById(_: UUID)(_: Seq[(String, String)]))
      .expects(*, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementRetrieveNotFound(agreementId: UUID) =
    (mockAgreementManagementService
      .getAgreementById(_: UUID)(_: Seq[(String, String)]))
      .expects(*, *)
      .once()
      .returns(Future.failed(AgreementNotFound(agreementId.toString)))

  def mockAgreementsRetrieve(result: Seq[Agreement]) =
    (mockAgreementManagementService
      .getAgreements(
        _: Option[String],
        _: Option[String],
        _: Option[String],
        _: Option[String],
        _: List[AgreementState],
        _: Option[String]
      )(_: Seq[(String, String)]))
      .expects(*, *, *, *, *, *, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementUpgrade(agreementId: UUID, seed: UpgradeAgreementSeed, result: Agreement) =
    (mockAgreementManagementService
      .upgradeById(_: UUID, _: UpgradeAgreementSeed)(_: Seq[(String, String)]))
      .expects(agreementId, seed, *)
      .once()
      .returns(Future.successful(result))

  def mockTenantRetrieve(tenantId: UUID, result: Tenant) =
    (mockTenantManagementService
      .getTenant(_: UUID)(_: Seq[(String, String)]))
      .expects(tenantId, *)
      .once()
      .returns(Future.successful(result))

  def mockTenantRetrieveNotFound(tenantId: UUID, result: Tenant) =
    (mockTenantManagementService
      .getTenant(_: UUID)(_: Seq[(String, String)]))
      .expects(tenantId, *)
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

  def mockPartyManagementRetrieve(institution: Institution) =
    (mockPartyManagementService
      .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
      .expects(*, *, *)
      .once()
      .returns(Future.successful(institution))

  def mockUserRegistryRetrieve(user: UserResource) =
    (mockUserRegistryService
      .getUserById(_: UUID)(_: Seq[(String, String)]))
      .expects(*, *)
      .once()
      .returns(Future.successful(user))

  def mockAttributeManagementServiceRetrieve(attribute: ClientAttribute) =
    (mockAttributeManagementService
      .getAttribute(_: String)(_: Seq[(String, String)]))
      .expects(*, *)
      .returns(Future.successful(attribute))

  def mockPDFCreatorCreate =
    (mockPDFCreator.create(_: String, _: PDFPayload)).expects(*, *).returns(Future.successful(Array.empty))

  def mockFileManagerWrite =
    (mockFileManager
      .storeBytes(_: String, _: String)(_: String, _: String, _: Array[Byte]))
      .expects(*, *, *, *, *)
      .returns(Future.successful("path"))

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

  def mockContractCreation(
    agreement: Agreement,
    eService: EService,
    consumer: Tenant,
    expectedSeed: UpdateAgreementSeed
  ) = {
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
    mockPartyManagementRetrieve(SpecData.institution(UUID.randomUUID()))
    mockPartyManagementRetrieve(SpecData.institution(UUID.randomUUID()))
    mockUserRegistryRetrieve(SpecData.userResource("a", "b", "c"))
    mockUserRegistryRetrieve(SpecData.userResource("d", "e", "f"))
    mockTenantRetrieve(consumer.id, consumer)
    mockAgreementUpdate(agreement.id, expectedSeed, agreement)
    mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)
  }

  def mockAutomaticActivation(
    agreement: Agreement,
    eService: EService,
    consumer: Tenant,
    expectedSeed: UpdateAgreementSeed
  ) = {
    mockAgreementRetrieve(agreement)
    mockAgreementsRetrieve(Nil)
    mockEServiceRetrieve(eService.id, eService)
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockAttributeManagementServiceRetrieve(SpecData.clientAttribute(UUID.randomUUID()))
    mockTenantRetrieve(consumer.id, consumer)
    mockAgreementUpdate(
      agreement.id,
      expectedSeed,
      agreement.copy(state = expectedSeed.state, stamps = SpecData.activationStamps)
    )
    mockPDFCreatorCreate
    mockFileManagerWrite
    mockAgreementContract
    mockPartyManagementRetrieve(SpecData.institution(UUID.randomUUID()))
    mockPartyManagementRetrieve(SpecData.institution(UUID.randomUUID()))
    mockUserRegistryRetrieve(SpecData.userResource("a", "b", "c"))
    mockUserRegistryRetrieve(SpecData.userResource("d", "e", "f"))
    mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)
  }

  def mockSelfActivation(
    agreement: Agreement,
    eService: EService,
    consumer: Tenant,
    expectedSeed: UpdateAgreementSeed
  ) = {
    mockAgreementRetrieve(agreement)
    mockAgreementsRetrieve(Nil)
    mockEServiceRetrieve(eService.id, eService)
    mockTenantRetrieve(consumer.id, consumer)
    mockAgreementUpdate(
      agreement.id,
      expectedSeed,
      agreement.copy(state = expectedSeed.state, stamps = SpecData.activationStamps)
    )
    mockPDFCreatorCreate
    mockFileManagerWrite
    mockAgreementContract
    mockPartyManagementRetrieve(SpecData.institution(UUID.randomUUID()))
    mockPartyManagementRetrieve(SpecData.institution(UUID.randomUUID()))
    mockUserRegistryRetrieve(SpecData.userResource("a", "b", "c"))
    mockUserRegistryRetrieve(SpecData.userResource("d", "e", "f"))
    mockClientStateUpdate(agreement.eserviceId, agreement.consumerId, agreement.id, ClientComponentState.ACTIVE)
  }
}
