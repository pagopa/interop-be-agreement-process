package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.agreementprocess.api.AgreementApiMarshaller
import it.pagopa.interop.agreementprocess.model._
import spray.json._

case object AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerAgreements: ToEntityMarshaller[Agreements] = sprayJsonMarshaller[Agreements]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def fromEntityUnmarshallerAgreementRejectionPayload
    : FromEntityUnmarshaller[AgreementRejectionPayload] = sprayJsonUnmarshaller[AgreementRejectionPayload]

  override implicit def toEntityMarshallerDocument: ToEntityMarshaller[Document] = sprayJsonMarshaller[Document]

  override implicit def fromEntityUnmarshallerDocumentSeed: FromEntityUnmarshaller[DocumentSeed] =
    sprayJsonUnmarshaller[DocumentSeed]

  override implicit def fromEntityUnmarshallerAgreementSubmissionPayload
    : FromEntityUnmarshaller[AgreementSubmissionPayload] = sprayJsonUnmarshaller[AgreementSubmissionPayload]

  override implicit def fromEntityUnmarshallerAgreementUpdatePayload: FromEntityUnmarshaller[AgreementUpdatePayload] =
    sprayJsonUnmarshaller[AgreementUpdatePayload]

  implicit def toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations] =
    sprayJsonMarshaller[CompactOrganizations]

  override implicit def toEntityMarshallerCompactEServices: ToEntityMarshaller[CompactEServices] =
    sprayJsonMarshaller[CompactEServices]

  override implicit def fromEntityUnmarshallerComputeAgreementStatePayload
    : FromEntityUnmarshaller[ComputeAgreementStatePayload] = sprayJsonUnmarshaller[ComputeAgreementStatePayload]
}
