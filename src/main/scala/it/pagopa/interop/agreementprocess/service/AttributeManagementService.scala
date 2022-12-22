package it.pagopa.interop.agreementprocess.service

import scala.concurrent.Future

trait AttributeManagementService {
  def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute]
}
