package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.ConsumerApiMarshaller
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Attribute, Attributes, Problem}
import spray.json.RootJsonFormat

class ConsumerApiMarshallerImpl extends ConsumerApiMarshaller {

  implicit def attributeJsonFormat: RootJsonFormat[Attribute] = jsonFormat9(Attribute)

  override implicit def toEntityMarshallerAttributes: ToEntityMarshaller[Attributes] =
    sprayJsonMarshaller[Attributes](jsonFormat3(Attributes))

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = jsonFormat3(Problem)
}
