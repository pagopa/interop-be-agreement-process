package it.pagopa.interop.agreementprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.utils.AkkaUtils.getFutureBearer
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.commons.jwt.{authorizeInterop, hasPermissions}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit def verifiedAttributeJsonFormat: RootJsonFormat[VerifiedAttribute]   = jsonFormat1(VerifiedAttribute)
  implicit def declaredAttributeJsonFormat: RootJsonFormat[DeclaredAttribute]   = jsonFormat1(DeclaredAttribute)
  implicit def certifiedAttributeJsonFormat: RootJsonFormat[CertifiedAttribute] = jsonFormat1(CertifiedAttribute)
  implicit def documentSeedJsonFormat: RootJsonFormat[DocumentSeed]             = jsonFormat5(DocumentSeed)
  implicit def documentJsonFormat: RootJsonFormat[Document]                     = jsonFormat6(Document)
  implicit def agreementJsonFormat: RootJsonFormat[Agreement]                   = jsonFormat18(Agreement)
  implicit def agreementPayloadJsonFormat: RootJsonFormat[AgreementPayload]     = jsonFormat3(AgreementPayload)
  implicit def agreementRejectionPayloadJsonFormat: RootJsonFormat[AgreementRejectionPayload] =
    jsonFormat1(AgreementRejectionPayload)
  implicit def problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit def problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  final val serviceErrorCodePrefix: String = "005"
  final val defaultProblemType: String     = "about:blank"
  final val defaultErrorMessage: String    = "Unknown error"

  def problemOf(httpError: StatusCode, error: ComponentError): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def problemOf(httpError: StatusCode, errors: List[ComponentError]): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = errors.map(error =>
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def validateBearer(contexts: Seq[(String, String)], jwt: JWTReader)(implicit ec: ExecutionContext): Future[String] =
    for {
      bearer <- getFutureBearer(contexts)
      _      <- jwt.getClaims(bearer).toFuture
    } yield bearer

  private[impl] def authorize(roles: String*)(
    route: => Route
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorizeInterop(hasPermissions(roles: _*), problemOf(StatusCodes.Forbidden, OperationForbidden)) {
      route
    }
}
