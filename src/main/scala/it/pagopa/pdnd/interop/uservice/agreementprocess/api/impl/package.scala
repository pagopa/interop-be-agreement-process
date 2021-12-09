package it.pagopa.pdnd.interop.uservice.agreementprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.pdnd.interop.uservice._
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  type ManagementEService     = catalogmanagement.client.model.EService
  type ManagementOrganization = partymanagement.client.model.Organization
  type ManagementAgreement    = agreementmanagement.client.model.Agreement
  type ManagementAttributes   = catalogmanagement.client.model.Attributes

  implicit def organizationJsonFormat: RootJsonFormat[Organization]               = jsonFormat2(Organization)
  implicit def activeDescriptorJsonFormat: RootJsonFormat[ActiveDescriptor]       = jsonFormat3(ActiveDescriptor)
  implicit def eServiceJsonFormat: RootJsonFormat[EService]                       = jsonFormat4(EService)
  implicit def agreementAttributesJsonFormat: RootJsonFormat[AgreementAttributes] = jsonFormat2(AgreementAttributes)
  implicit def agreementJsonFormat: RootJsonFormat[Agreement]                     = jsonFormat9(Agreement)
  implicit def agreementPayloadJsonFormat: RootJsonFormat[AgreementPayload]       = jsonFormat3(AgreementPayload)
  implicit def attributeJsonFormat: RootJsonFormat[Attribute]                     = jsonFormat9(Attribute)
  implicit def attributesJsonFormat: RootJsonFormat[Attributes]                   = jsonFormat3(Attributes)
  implicit def problemErrorFormat: RootJsonFormat[ProblemError]                   = jsonFormat2(ProblemError)
  implicit def problemFormat: RootJsonFormat[Problem]                             = jsonFormat5(Problem)

  def problemOf(
    httpError: StatusCode,
    errorCode: String,
    exception: Throwable = new RuntimeException(),
    defaultMessage: String = "Unknown error"
  ): Problem =
    Problem(
      `type` = "about:blank",
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors =
        Seq(ProblemError(code = s"001-$errorCode", detail = Option(exception.getMessage).getOrElse(defaultMessage)))
    )
}
