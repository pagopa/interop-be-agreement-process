package it.pagopa.interop.agreementprocess.service

import cats.implicits._
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.DescriptorNotInExpectedState
import it.pagopa.interop.catalogmanagement.client.model._
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

}

object CatalogManagementService {

  // TODO
  //  Verify eServices states
  //  Rename
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
    // TODO Check which descriptor states are allowed
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.SUSPENDED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }
  // TODO end todo

  def validateEServiceDescriptorStatus(
    eService: EService,
    descriptorId: UUID,
    allowedStates: List[EServiceDescriptorState]
  ): Future[Unit] = {
    val descriptorStatus = eService.descriptors.find(_.id == descriptorId).map(_.state)

    // Not using whenA on Future.failed because it requires an ExecutionContext, which is not actually needed here
    Left[DescriptorNotInExpectedState, Unit](DescriptorNotInExpectedState(eService.id, descriptorId, allowedStates))
      .withRight[Unit]
      .unlessA(descriptorStatus.exists(status => allowedStates.contains(status)))
      .toFuture
  }

  def hasEserviceNewPublishedVersion(latestVersion: Option[Long], currentVersion: Option[Long]): Future[Boolean] =
    (latestVersion, currentVersion) match {
      case (Some(l), Some(c)) if l > c => Future.successful(true)
      case (Some(_), None)             => Future.successful(true)
      case _ => Future.failed[Boolean](new RuntimeException("No new versions exist for this agreement!"))
    }

}
