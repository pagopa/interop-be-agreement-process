package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.AgreementApiMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Agreement, AgreementPayload, Audience, Problem}
import spray.json._

class AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def toEntityMarshallerAudience: ToEntityMarshaller[Audience] =
    sprayJsonMarshaller[Audience](jsonFormat2(Audience))
  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] =
    sprayJsonMarshaller[Problem](jsonFormat3(Problem))
  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload](jsonFormat4(AgreementPayload))
  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] =
    sprayJsonMarshaller[Agreement](jsonFormat1(Agreement))
}
