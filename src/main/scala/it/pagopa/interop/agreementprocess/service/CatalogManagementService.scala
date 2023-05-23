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
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }
  def validateCreationOnDescriptor(eservice: EService, descriptorId: UUID): Future[Unit]   = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.SUSPENDED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }
  def validateSubmitOnDescriptor(eservice: EService, descriptorId: UUID): Future[Unit]     = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.SUSPENDED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateEServiceDescriptorStatus(
    eService: EService,
    descriptorId: UUID,
    allowedStates: List[EServiceDescriptorState]
  ): Future[Unit] = {
    val validDescriptorState = List(EServiceDescriptorState.SUSPENDED, EServiceDescriptorState.PUBLISHED)
    val descriptorStatus     =
      eService.descriptors
        .filterNot(_.state == EServiceDescriptorState.DRAFT)
        .sortBy(_.version.toLong)
        .lastOption
        .filter(_.id == descriptorId)
        .map(_.state)

    if (descriptorStatus.exists(validDescriptorState.contains)) {
      // Not using whenA on Future.failed because it requires an ExecutionContext, which is not actually needed here
      Either
        .left[DescriptorNotInExpectedState, Unit](
          DescriptorNotInExpectedState(eService.id, descriptorId, allowedStates)
        )
        .unlessA(descriptorStatus.exists(status => allowedStates.contains(status)))
        .toFuture
    } else Future.failed(NotValidEServiceDescriptorId(descriptorId))
  }

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
