package it.pagopa.interop.agreementprocess.service.impl.util

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Informing, Suite}
import org.scalatest.matchers.should.Matchers

trait AuthzScalatestRouteTest extends ScalatestRouteTest with Matchers with Informing {
  suite: Suite =>

  // when request occurs, check that it does not return neither 401 nor 403
  def validRoleCheck(role: String, request: => HttpRequest, r: => Route): Unit =
    request ~> r ~> check {
      status should not be StatusCodes.Unauthorized
      status should not be StatusCodes.Forbidden
      info(s"role $role is properly authorized")
    }

  // when request occurs, check that it forbids invalid role
  def invalidRoleCheck(role: String, request: => HttpRequest, r: => Route): Unit = {
    request ~> r ~> check {
      status shouldBe StatusCodes.Forbidden
      info(s"role $role is properly forbidden since it is invalid")
    }
  }
}
