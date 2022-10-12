package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import java.util.UUID
import scala.concurrent.Future

trait UserRegistryService {

  def getUserById(id: UUID)(implicit contexts: Seq[(String, String)]): Future[UserResource]
}
