package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import it.pagopa.interop.agreementprocess.api.HealthApiService
import it.pagopa.interop.agreementprocess.model.Problem

class HealthServiceApiImpl extends HealthApiService {

  /** Code: 200, Message: successful operation, DataType: Problem
    */
  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = getStatus200(
    Problem(
      `type` = "about:blank",
      status = StatusCodes.OK.intValue,
      title = StatusCodes.OK.defaultMessage,
      errors = Seq.empty
    )
  )

}
