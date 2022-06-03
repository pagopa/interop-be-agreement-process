package it.pagopa.interop.agreementprocess

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.dwickern.macros.NameOf.nameOf
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiMarshallerImpl._
import it.pagopa.interop.agreementprocess.api.impl.AgreementApiServiceImpl
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl.util.AuthorizedRoutes
import it.pagopa.interop.agreementprocess.service.impl.util.FakeDependencies._
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.USER_ROLES
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.annotation.nowarn
import scala.concurrent.ExecutionContext

class AgreementApiAuthzSpec extends AnyWordSpecLike with MockFactory with ScalatestRouteTest with Matchers {

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

  val service: AgreementApiServiceImpl = AgreementApiServiceImpl(
    fakeAgreementManagementService,
    fakeCatalogManagementService,
    fakePartyManagementService,
    fakeAttributeManagementService,
    fakeAuthorizationManagementService,
    fakeJWTReader
  )(ExecutionContext.global)

  // when request occurs, check that it does not return neither 401 nor 403
  def validRoleCheck(role: String, request: => HttpRequest, r: => Route) =
    request ~> r ~> check {
      status should not be StatusCodes.Unauthorized
      status should not be StatusCodes.Forbidden
      info(s"role $role is properly authorized")
    }

  // when request occurs, check that it forbids invalid role
  def invalidRoleCheck(role: String, request: => HttpRequest, r: => Route) = {
    request ~> r ~> check {
      status shouldBe StatusCodes.Forbidden
      info(s"role $role is properly forbidden since it is invalid")
    }
  }

  "Agreement api operation authorization spec" should {
    "accept authorized roles for activateAgreement" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.activateAgreement(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.activateAgreement("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.activateAgreement("fake", "fake")
      )
    }

    "accept authorized roles for suspendAgreement" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.suspendAgreement(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.suspendAgreement("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.suspendAgreement("fake", "fake")
      )
    }
    "accept authorized roles for createAgreement" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.createAgreement(???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val agreementPayload = AgreementPayload(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        consumerId = UUID.randomUUID()
      )

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.createAgreement(agreementPayload)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.createAgreement(agreementPayload)
      )
    }
    "accept authorized roles for getAgreements" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.getAgreements(???, ???, ???, ???, ???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getAgreements(None, None, None, None, None, None)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.getAgreements(None, None, None, None, None, None)
      )
    }
    "accept authorized roles for getAgreementById" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.getAgreementById(???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getAgreementById("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getAgreementById("fake"))
    }
    "accept authorized roles for verifyAgreementAttribute" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.verifyAgreementAttribute(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.verifyAgreementAttribute("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.verifyAgreementAttribute("fake", "fake")
      )
    }
    "accept authorized roles for upgradeAgreementById" in {
      @nowarn
      val routeName = nameOf[AgreementApiServiceImpl](_.upgradeAgreementById(???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.upgradeAgreementById("fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.upgradeAgreementById("fake")
      )
    }
  }
}
