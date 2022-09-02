package it.pagopa.interop.agreementprocess.api.impl

import it.pagopa.interop.agreementmanagement.client.model.Agreement
import it.pagopa.interop.agreementprocess.service.CatalogManagementService
import it.pagopa.interop.catalogmanagement.client.model.EService

import scala.concurrent.{ExecutionContext, Future}

object AgreementFilter {

  // TODO can we make this function without side effects?

  /** Returns only the latest agreement for each EService.
    * The latest agreement is the agreement linked to the most recent descriptor version
    */
  def filterAgreementsByLatestVersion(
    catalogManagementService: CatalogManagementService,
    agreements: Seq[Agreement]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Agreement]] = {
    val uniqueEServicesIds = agreements.map(_.eserviceId).distinct
    val eServices          = Future.traverse(uniqueEServicesIds)(catalogManagementService.getEServiceById)
    eServices.map(process(agreements))
  }

  private def process(agreements: Seq[Agreement])(eServices: Seq[EService]): Seq[Agreement] =
    eServices
      .map(e => (e.id, e.descriptors.sortBy(_.version).lastOption.map(_.id)))
      .collect { case (eServiceId, Some(descriptorId)) => (eServiceId, descriptorId) }
      .flatMap { case (eServiceId, descriptorId) =>
        agreements.find(a => a.eserviceId == eServiceId && a.descriptorId == descriptorId)
      }
}
