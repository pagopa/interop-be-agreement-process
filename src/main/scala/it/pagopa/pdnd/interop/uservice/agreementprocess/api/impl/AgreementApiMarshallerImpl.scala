package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import spray.json._

class AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  implicit def organizationJsonFormat: RootJsonFormat[Organization]               = jsonFormat2(Organization)
  implicit def eServiceJsonFormat: RootJsonFormat[EService]                       = jsonFormat3(EService)
  implicit def attributeJsonFormat: RootJsonFormat[Attribute]                     = jsonFormat9(Attribute)
  implicit def agreementAttributesJsonFormat: RootJsonFormat[AgreementAttributes] = jsonFormat2(AgreementAttributes)
  implicit def agreementJsonFormat: RootJsonFormat[Agreement]                     = jsonFormat8(Agreement)

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] =
    sprayJsonMarshaller[Problem](jsonFormat3(Problem))

  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload](jsonFormat3(AgreementPayload))

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] =
    sprayJsonMarshaller[Agreement]

  override implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]] =
    sprayJsonMarshaller[Seq[Agreement]]
}
