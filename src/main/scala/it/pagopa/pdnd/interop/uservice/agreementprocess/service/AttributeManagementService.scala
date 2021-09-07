package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementprocess.model.Attributes

import scala.concurrent.Future

trait AttributeManagementService {
  def getAttribute(attributeId: String): Future[ClientAttribute]
  def getAttributes(certified: Seq[String], declared: Seq[String], verified: Seq[String]): Future[Attributes]
}
