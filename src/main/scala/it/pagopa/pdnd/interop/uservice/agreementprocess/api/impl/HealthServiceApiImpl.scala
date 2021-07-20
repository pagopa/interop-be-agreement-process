package it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.HealthApiService
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.Problem

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class HealthServiceApiImpl extends HealthApiService {

  /** Code: 200, Message: successful operation, DataType: Problem
    */
  override def getStatus()(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = getStatus200(Problem(None, 200, "OK"))
}
