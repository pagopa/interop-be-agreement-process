package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementprocess.model.Attributes

import scala.concurrent.Future

trait AttributeManagementService {
  def getAttributes(certified: Seq[String], declared: Seq[String], verified: Seq[String]): Future[Attributes]
}
