package it.pagopa.interop.agreementprocess.service

import cats.implicits._
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait CatalogManagementService {

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

}

object CatalogManagementService {

  def validateActivationOnDescriptor(eservice: EService, descriptorId: UUID): Future[Unit] = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.DEPRECATED, EServiceDescriptorState.SUSPENDED)
    validateDescriptor(eservice, descriptorId, allowedStatus)
  }
  def validateCreationOnDescriptor(eservice: EService, descriptorId: UUID): Future[Unit]   = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.SUSPENDED)
    validateLatestDescriptor(eservice, descriptorId, allowedStatus)
  }
  def validateSubmitOnDescriptor(eservice: EService, descriptorId: UUID): Future[Unit]     = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.SUSPENDED)
    validateLatestDescriptor(eservice, descriptorId, allowedStatus)
  }

  def validateLatestDescriptor(
    eService: EService,
    descriptorId: UUID,
    allowedStates: List[EServiceDescriptorState]
  ): Future[Unit] = {

    eService.descriptors
      .filterNot(_.state == EServiceDescriptorState.DRAFT)
      .maxByOption(_.version.toLong)
      .find(_.id == descriptorId)
      .toRight(NotLatestEServiceDescriptor(descriptorId))
      .flatMap(d => validateDescriptorState(eService.id, descriptorId, d.state, allowedStates))
      .toFuture
  }

  def validateDescriptor(eService: EService, descriptorId: UUID, allowedStates: List[EServiceDescriptorState]) =
    eService.descriptors
      .find(_.id == descriptorId)
      .toRight(DescriptorNotFound(eService.id, descriptorId))
      .flatMap(d => validateDescriptorState(eService.id, descriptorId, d.state, allowedStates))
      .toFuture

  def validateDescriptorState(
    eServiceId: UUID,
    descriptorId: UUID,
    descriptorState: EServiceDescriptorState,
    allowedStates: List[EServiceDescriptorState]
  ) =
    Either
      .left[DescriptorNotInExpectedState, Unit](DescriptorNotInExpectedState(eServiceId, descriptorId, allowedStates))
      .unlessA(allowedStates.contains(descriptorState))

  def getEServiceNewerPublishedDescriptor(eService: EService, currentDescriptorId: UUID)(implicit
    ec: ExecutionContext
  ): Future[EServiceDescriptor] = for {
    latestActiveEServiceDescriptor <- eService.descriptors
      .find(_.state == EServiceDescriptorState.PUBLISHED)
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
