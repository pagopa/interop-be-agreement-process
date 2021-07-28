package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, EService}

import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {
  def flattenAttributes(verified: Seq[Attribute]): Future[Seq[UUID]]
  def verifyProducerMatch(eserviceProducerId: UUID, seedProducerId: UUID): Future[Boolean]
  def getEServiceById(bearerToken: String, eServiceId: String): Future[EService]
  def checkEServiceActivation(eservice: EService): Future[EService]
}
