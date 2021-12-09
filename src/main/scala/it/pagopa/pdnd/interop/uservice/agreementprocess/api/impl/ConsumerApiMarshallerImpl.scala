package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.ConsumerApiMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Attributes, Problem}

class ConsumerApiMarshallerImpl extends ConsumerApiMarshaller {

  override implicit def toEntityMarshallerAttributes: ToEntityMarshaller[Attributes] = sprayJsonMarshaller[Attributes]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
