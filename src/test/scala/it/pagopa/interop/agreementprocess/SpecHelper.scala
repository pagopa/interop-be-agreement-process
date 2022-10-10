package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model.{
  Agreement,
  AgreementState,
  UpdateAgreementSeed,
  UpgradeAgreementSeed
}
import it.pagopa.interop.agreementprocess.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl}
import it.pagopa.interop.agreementprocess.api.{AgreementApiMarshaller, AgreementApiService}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{AgreementNotFound, EServiceNotFound}
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.authorizationmanagement.client.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientComponentState
}
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.commons.files.service.FileManager
import org.scalamock.scalatest.MockFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait SpecHelper extends MockFactory {

  final lazy val url: String =
    s"http://localhost:8088/agreement-process/${buildinfo.BuildInfo.interfaceVersion}"

  val bearerToken: String                      = "bearerToken"
  val requesterOrgId: UUID                     = UUID.randomUUID()
  implicit val contexts: Seq[(String, String)] =
    Seq("bearer" -> "bearerToken", ORGANIZATION_ID_CLAIM -> requesterOrgId.toString, USER_ROLES -> "admin")

  val agreementApiMarshaller: AgreementApiMarshaller = AgreementApiMarshallerImpl

  val mockAgreementManagementService: AgreementManagementService         = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService             = mock[CatalogManagementService]
  val mockTenantManagementService: TenantManagementService               = mock[TenantManagementService]
  val mockAttributeManagementService: AttributeManagementService         = mock[AttributeManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService = mock[AuthorizationManagementService]
  val mockFileManager: FileManager                                       = mock[FileManager]

  val service: AgreementApiService = AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockTenantManagementService,
    mockAttributeManagementService,
    mockAuthorizationManagementService,
    mockFileManager
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
      .expects(agreementId, seed, *)
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
      .getAgreementById(_: String)(_: Seq[(String, String)]))
      .expects(*, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementRetrieveNotFound(agreementId: UUID) =
    (mockAgreementManagementService
      .getAgreementById(_: String)(_: Seq[(String, String)]))
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

  def mockFileDeletion(containerPath: String, filePath: String) =
    (mockFileManager
      .delete(_: String)(_: String))
      .expects(containerPath, filePath)
      .once()
      .returns(Future.successful(true))
}
