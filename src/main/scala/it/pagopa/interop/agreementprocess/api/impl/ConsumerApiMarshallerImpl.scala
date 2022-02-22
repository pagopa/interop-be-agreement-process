package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.agreementprocess.api.ConsumerApiMarshaller
import it.pagopa.interop.agreementprocess.model.{Attributes, Problem}

class ConsumerApiMarshallerImpl extends ConsumerApiMarshaller {

  override implicit def toEntityMarshallerAttributes: ToEntityMarshaller[Attributes] = sprayJsonMarshaller[Attributes]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
