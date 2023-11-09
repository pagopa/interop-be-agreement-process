package it.pagopa.interop.agreementprocess.service

import scala.concurrent.{ExecutionContext, Future}
import it.pagopa.interop.selfcare.v2.client.model.{Institution, UserResponse}
import java.util.UUID

trait SelfcareV2ClientService {

  def getInstitution(
    selfcareId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]

  def getUserById(selfcareId: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[UserResponse]
}
