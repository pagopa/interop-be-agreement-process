package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.agreementprocess.api.impl.{AgreementApiMarshallerImpl, AgreementApiServiceImpl}
import it.pagopa.interop.agreementprocess.api.{AgreementApi, AgreementApiMarshaller, AgreementApiService, HealthApi}
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import it.pagopa.interop.tenantmanagement.client.model.Tenant
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

  val agreementApiMarshaller: AgreementApiMarshaller                     = AgreementApiMarshallerImpl
  val mockHealthApi: HealthApi                                           = mock[HealthApi]
  val mockAgreementApi: AgreementApi                                     = mock[AgreementApi]
  val mockPartyManagementService: PartyManagementService                 = mock[PartyManagementService]
  val mockAgreementManagementService: AgreementManagementService         = mock[AgreementManagementService]
  val mockCatalogManagementService: CatalogManagementService             = mock[CatalogManagementService]
  val mockTenantManagementService: TenantManagementService               = mock[TenantManagementService]
  val mockAttributeManagementService: AttributeManagementService         = mock[AttributeManagementService]
  val mockAuthorizationManagementService: AuthorizationManagementService = mock[AuthorizationManagementService]

  val service: AgreementApiService = AgreementApiServiceImpl(
    mockAgreementManagementService,
    mockCatalogManagementService,
    mockPartyManagementService,
    mockTenantManagementService,
    mockAttributeManagementService,
    mockAuthorizationManagementService
  )(ExecutionContext.global)

  def mockEServiceRetrieve(eServiceId: UUID, result: EService) =
    (mockCatalogManagementService
      .getEServiceById(_: UUID)(_: Seq[(String, String)]))
      .expects(eServiceId, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementCreation(result: Agreement) =
    (mockAgreementManagementService
      .createAgreement(_: UUID, _: UUID, _: UUID, _: UUID)(_: Seq[(String, String)]))
      .expects(*, *, *, *, *)
      .once()
      .returns(Future.successful(result))

  def mockAgreementsRetrieve(result: Seq[Agreement]) =
    (mockAgreementManagementService
      .getAgreements(
        _: Option[String],
        _: Option[String],
        _: Option[String],
        _: Option[String],
        _: List[AgreementState]
      )(_: Seq[(String, String)]))
      .expects(*, *, *, *, *, *)
      .once()
      .returns(Future.successful(result))

  def mockTenantRetrieve(tenantId: UUID, result: Tenant) =
    (mockTenantManagementService
      .getTenant(_: UUID)(_: Seq[(String, String)]))
      .expects(tenantId, *)
      .once()
      .returns(Future.successful(result))

}
