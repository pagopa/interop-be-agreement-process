package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementprocess.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.util.FakeDependencies._
import it.pagopa.interop.agreementprocess.util.{AuthorizedRoutes, AuthzScalatestRouteTest}
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

  val service: AgreementApiServiceImpl = AgreementApiServiceImpl(
    fakeAgreementManagementService,
    fakeCatalogManagementService,
    fakeTenantManagementService,
    fakeAttributeManagementService,
    fakeAuthorizationManagementService
  )(ExecutionContext.global)

  "Agreement api operation authorization spec" should {

    "accept authorized roles for createAgreement" in {
      val endpoint         = AuthorizedRoutes.endpoints("createAgreement")
      val agreementPayload = AgreementPayload(eserviceId = UUID.randomUUID(), descriptorId = UUID.randomUUID())

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.createAgreement(agreementPayload) }
      )
    }

    "accept authorized roles for submitAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("submitAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.submitAgreement("fake") })
    }

    "accept authorized roles for activateAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("activateAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.activateAgreement("fake") })
    }

    "accept authorized roles for suspendAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.suspendAgreement("fake") })
    }

    "accept authorized roles for getAgreements" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreements")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getAgreements(None, None, None, None, "", None) }
      )
    }
    "accept authorized roles for getAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreementById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAgreementById("fake") })
    }

    "accept authorized roles for upgradeAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("upgradeAgreementById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.upgradeAgreementById("fake") })
    }
  }
}
