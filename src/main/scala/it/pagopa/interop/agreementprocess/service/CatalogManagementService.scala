package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{
  DescriptorNotFound,
  DescriptorNotInExpectedState
}
import it.pagopa.interop.agreementprocess.{model => AgreementProcess}
import it.pagopa.interop.catalogmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try
import cats.implicits._
import it.pagopa.interop.commons.utils.TypeConversions._

trait CatalogManagementService {

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

}

object CatalogManagementService {
  def getActiveDescriptorOption(
    eservice: EService,
    currentDescriptor: EServiceDescriptor
  ): Future[Option[EServiceDescriptor]] = Future.successful {
    val currentVersion = currentDescriptor.version.toLongOption
    eservice.descriptors
      .find(d =>
        d.state == EServiceDescriptorState.PUBLISHED && Ordering[Option[Long]]
          .gt(d.version.toLongOption, currentVersion)
      )
  }

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
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.SUSPENDED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }
  // TODO end todo

  // TODO Rename
  def validateOperationOnDescriptor(eservice: EService, descriptorId: UUID): Future[Unit] = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.DEPRECATED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

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

  def getDescriptorAudience(eservice: EService, descriptorId: UUID): Future[Seq[String]] = {
    val audience = eservice.descriptors.find(_.id == descriptorId).map(_.audience)

    audience match {
      case Some(aud) => Future.successful[Seq[String]](aud)
      case None      =>
        Future.failed[Seq[String]](DescriptorNotFound(eservice.id.toString, descriptorId.toString))
    }
  }

  def flattenAttributes(attributes: Seq[Attribute]): Future[Seq[AttributeValue]] = Future.fromTry {
    Try {
      attributes
        .flatMap(attribute => attribute.group.toSeq.flatten ++ attribute.single.toSeq)
    }
  }

  def hasEserviceNewPublishedVersion(latestVersion: Option[Long], currentVersion: Option[Long]): Future[Boolean] =
    (latestVersion, currentVersion) match {
      case (Some(l), Some(c)) if l > c => Future.successful(true)
      case (Some(_), None)             => Future.successful(true)
      case _ => Future.failed[Boolean](new RuntimeException("No new versions exist for this agreement!"))
    }

  def descriptorStateToApi(state: EServiceDescriptorState): AgreementProcess.EServiceDescriptorState = state match {
    case EServiceDescriptorState.DRAFT      => AgreementProcess.EServiceDescriptorState.DRAFT
    case EServiceDescriptorState.PUBLISHED  => AgreementProcess.EServiceDescriptorState.PUBLISHED
    case EServiceDescriptorState.DEPRECATED => AgreementProcess.EServiceDescriptorState.DEPRECATED
    case EServiceDescriptorState.SUSPENDED  => AgreementProcess.EServiceDescriptorState.SUSPENDED
    case EServiceDescriptorState.ARCHIVED   => AgreementProcess.EServiceDescriptorState.ARCHIVED
  }
}
