package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._
import spray.json._

class AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  implicit def organizationJsonFormat: RootJsonFormat[Organization] = jsonFormat2(Organization)
  implicit def eServiceJsonFormat: RootJsonFormat[EService]         = jsonFormat2(EService)
  implicit def attributeJsonFormat: RootJsonFormat[Attribute]       = jsonFormat8(Attribute)
  implicit def agreementJsonFormat: RootJsonFormat[Agreement]       = jsonFormat5(Agreement)

  override implicit def toEntityMarshallerAudience: ToEntityMarshaller[Audience] =
    sprayJsonMarshaller[Audience](jsonFormat2(Audience))

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] =
    sprayJsonMarshaller[Problem](jsonFormat3(Problem))

  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload](jsonFormat4(AgreementPayload))

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] =
    sprayJsonMarshaller[Agreement]

  override implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]] =
    sprayJsonMarshaller[Seq[Agreement]]
}
