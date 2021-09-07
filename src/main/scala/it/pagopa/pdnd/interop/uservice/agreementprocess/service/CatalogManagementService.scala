package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, AttributeValue, EService}

import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {
  def flattenAttributes(attributes: Seq[Attribute]): Future[Seq[AttributeValue]]
  def verifyProducerMatch(eserviceProducerId: UUID, seedProducerId: UUID): Future[Boolean]
  def getEServiceById(bearerToken: String, eServiceId: UUID): Future[EService]
  def checkEServiceActivation(eservice: EService): Future[EService]
}
