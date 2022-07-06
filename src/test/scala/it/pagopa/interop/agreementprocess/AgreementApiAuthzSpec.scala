package it.pagopa.interop.agreementprocess

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.util.FakeDependencies._
import it.pagopa.interop.agreementprocess.util.{AuthorizedRoutes, AuthzScalatestRouteTest}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.ExecutionContext

class AgreementApiAuthzSpec extends AnyWordSpecLike with MockFactory with AuthzScalatestRouteTest {

  val fakeAgreementManagementService: AgreementManagementService         = new FakeAgreementManagementService()
  val fakeCatalogManagementService: CatalogManagementService             = new FakeCatalogManagementService()
  val fakePartyManagementService: PartyManagementService                 = new FakePartyManagementService()
  val fakeAttributeManagementService: AttributeManagementService         = new FakeAttributeManagementService()
  val fakeAuthorizationManagementService: AuthorizationManagementService = new FakeAuthorizationManagementService()
  val fakeJWTReader: JWTReader                                           = new DefaultJWTReader with PublicKeysHolder {
    var publicKeyset: Map[KID, SerializedKey] = Map.empty

    override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
      getClaimsVerifier(audience = Set("test"))
  }
  val fakeFileManager: FileManager                                       = mock[FileManager]
  val fakePDFCreator: PDFCreator                                         = mock[PDFCreator]
  val fakeUUIDSupplier: UUIDSupplier                                     = mock[UUIDSupplier]

  val service: AgreementApiServiceImpl = AgreementApiServiceImpl(
    fakeAgreementManagementService,
    fakeCatalogManagementService,
    fakePartyManagementService,
    fakeAttributeManagementService,
    fakeAuthorizationManagementService,
    fakeJWTReader,
    fakeFileManager,
    fakePDFCreator,
    fakeUUIDSupplier
  )(ExecutionContext.global)

  "Agreement api operation authorization spec" should {
    "accept authorized roles for activateAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("activateAgreement")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.activateAgreement("fake", "fake") }
      )
    }

    "accept authorized roles for suspendAgreement" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendAgreement")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.suspendAgreement("fake", "fake") })
    }
    "accept authorized roles for createAgreement" in {
      val endpoint         = AuthorizedRoutes.endpoints("createAgreement")
      val agreementPayload = AgreementPayload(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = UUID.randomUUID()
      )

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.createAgreement(agreementPayload) }
      )
    }
    "accept authorized roles for getAgreements" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreements")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getAgreements(None, None, None, None, None, None) }
      )
    }
    "accept authorized roles for getAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("getAgreementById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAgreementById("fake") })
    }

    "accept authorized roles for verifyAgreementAttribute" in {
      val endpoint = AuthorizedRoutes.endpoints("verifyAgreementAttribute")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.verifyAgreementAttribute("fake", "fake") }
      )
    }

    "accept authorized roles for upgradeAgreementById" in {
      val endpoint = AuthorizedRoutes.endpoints("upgradeAgreementById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.upgradeAgreementById("fake") })
    }
  }
}
