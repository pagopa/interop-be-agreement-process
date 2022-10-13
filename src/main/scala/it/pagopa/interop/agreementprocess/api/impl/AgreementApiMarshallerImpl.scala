package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.agreementprocess.api.AgreementApiMarshaller
import it.pagopa.interop.agreementprocess.model._
import spray.json._

import java.io.File
import java.nio.charset.StandardCharsets
import scala.io.{BufferedSource, Codec}

case object AgreementApiMarshallerImpl extends AgreementApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]] =
    sprayJsonMarshaller[Seq[Agreement]]

  override implicit def fromEntityUnmarshallerAgreementRejectionPayload
    : FromEntityUnmarshaller[AgreementRejectionPayload] = sprayJsonUnmarshaller[AgreementRejectionPayload]

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File] =
    Marshaller.withFixedContentType(ContentTypes.`application/octet-stream`) { f =>
      val source: BufferedSource = scala.io.Source.fromFile(f.getPath)(Codec(StandardCharsets.UTF_8.name))
      val out: String            = source.mkString
      source.close()
      out.getBytes(StandardCharsets.UTF_8.name)
    }
}
