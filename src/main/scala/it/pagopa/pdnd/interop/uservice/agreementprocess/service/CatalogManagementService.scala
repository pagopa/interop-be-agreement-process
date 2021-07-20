package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EService

import scala.concurrent.Future

trait CatalogManagementService {
  def getEServiceById(bearerToken: String, eServiceId: String): Future[EService]
}
