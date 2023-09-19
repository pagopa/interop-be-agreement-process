package it.pagopa.interop.agreementprocess.service

import cats.syntax.all._
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.catalogmanagement.model._
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.cqrs.service.ReadModelService

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait CatalogManagementService {

  def getEServiceById(eServiceId: UUID)(implicit ec: ExecutionContext, readModel: ReadModelService): Future[CatalogItem]

}

object CatalogManagementService {

  def validateActivationOnDescriptor(eservice: CatalogItem, descriptorId: UUID): Future[CatalogDescriptor] = {
    val allowedStatus: List[CatalogDescriptorState] =
      List(Published, Deprecated, Suspended)
    validateDescriptor(eservice, descriptorId, allowedStatus)
  }
  def validateCreationOnDescriptor(eservice: CatalogItem, descriptorId: UUID): Future[CatalogDescriptor]   = {
    val allowedStatus: List[CatalogDescriptorState] =
      List(Published)
    validateLatestDescriptor(eservice, descriptorId, allowedStatus)
  }
  def validateSubmitOnDescriptor(eservice: CatalogItem, descriptorId: UUID): Future[CatalogDescriptor]     = {
    val allowedStatus: List[CatalogDescriptorState] =
      List(Published, Suspended)
    validateLatestDescriptor(eservice, descriptorId, allowedStatus)
  }

  def validateLatestDescriptor(
    eService: CatalogItem,
    descriptorId: UUID,
    allowedStates: List[CatalogDescriptorState]
  ): Future[CatalogDescriptor] = {

    eService.descriptors
      .filterNot(_.state == Draft)
      .maxByOption(_.version.toLong)
      .find(_.id == descriptorId)
      .toRight(NotLatestEServiceDescriptor(descriptorId))
      .flatMap(d => validateDescriptorState(eService.id, descriptorId, d.state, allowedStates).as(d))
      .toFuture
  }

  def validateDescriptor(eService: CatalogItem, descriptorId: UUID, allowedStates: List[CatalogDescriptorState]) =
    eService.descriptors
      .find(_.id == descriptorId)
      .toRight(DescriptorNotFound(eService.id, descriptorId))
      .flatMap(d => validateDescriptorState(eService.id, descriptorId, d.state, allowedStates).as(d))
      .toFuture

  def validateDescriptorState(
    eServiceId: UUID,
    descriptorId: UUID,
    descriptorState: CatalogDescriptorState,
    allowedStates: List[CatalogDescriptorState]
  ) =
    Either
      .left[DescriptorNotInExpectedState, Unit](DescriptorNotInExpectedState(eServiceId, descriptorId, allowedStates))
      .unlessA(allowedStates.contains(descriptorState))

  def getEServiceNewerPublishedDescriptor(eService: CatalogItem, currentDescriptorId: UUID)(implicit
    ec: ExecutionContext
  ): Future[CatalogDescriptor] = for {
    latestActiveEServiceDescriptor <- eService.descriptors
      .find(_.state == Published)
      .toFuture(PublishedDescriptorNotFound(eService.id))
    latestDescriptorVersion        <- latestActiveEServiceDescriptor.version.toLongOption.toFuture(
      UnexpectedVersionFormat(eService.id, latestActiveEServiceDescriptor.id)
    )
    currentDescriptor              <- eService.descriptors
      .find(_.id == currentDescriptorId)
      .toFuture(DescriptorNotFound(eService.id, latestActiveEServiceDescriptor.id))
    currentVersion                 <- currentDescriptor.version.toLongOption.toFuture(
      UnexpectedVersionFormat(eService.id, latestActiveEServiceDescriptor.id)
    )
    _                              <- Future
      .failed(NoNewerDescriptorExists(eService.id, currentDescriptorId))
      .unlessA(latestDescriptorVersion > currentVersion)
  } yield latestActiveEServiceDescriptor

}
