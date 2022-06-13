package it.pagopa.interop.agreementprocess.util

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.commons.utils.USER_ROLES
import org.scalatest.{Informing, Suite}
import org.scalatest.matchers.should.Matchers

trait AuthzScalatestRouteTest extends ScalatestRouteTest with Matchers with Informing {
  suite: Suite =>

  def validateAuthorization(endpoint: Endpoint, r: Seq[(String, String)] => Route): Unit = {
    endpoint.rolesInContexts.foreach(contexts => {
      validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })

    // given a fake role, check that its invocation is forbidden
    endpoint.invalidRoles.foreach(contexts => {
      invalidRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })
  }

  // when request occurs, check that it does not return neither 401 nor 403
  private def validRoleCheck(role: String, request: => HttpRequest, r: => Route) =
    request ~> r ~> check {
      status should not be StatusCodes.Unauthorized
      status should not be StatusCodes.Forbidden
      info(s"role $role is properly authorized")
    }

  // when request occurs, check that it forbids invalid role
  private def invalidRoleCheck(role: String, request: => HttpRequest, r: => Route) = {
    request ~> r ~> check {
      status shouldBe StatusCodes.Forbidden
      info(s"role $role is properly forbidden since it is invalid")
    }
  }
}
