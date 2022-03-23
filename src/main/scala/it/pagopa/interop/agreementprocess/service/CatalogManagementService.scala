package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.DescriptorNotFound
import it.pagopa.interop.agreementprocess.{model => AgreementProcess}
import it.pagopa.interop.catalogmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

trait CatalogManagementService {

  def getEServiceById(bearerToken: String)(eServiceId: UUID): Future[EService]

}

object CatalogManagementService {
  def getActiveDescriptorOption(
    eservice: EService,
    currentDescriptor: EServiceDescriptor
  ): Future[Option[EServiceDescriptor]] = {

    Future.successful {
      val currentVersion = currentDescriptor.version.toLongOption
      eservice.descriptors
        .find(d =>
          d.state == EServiceDescriptorState.PUBLISHED && Ordering[Option[Long]]
            .gt(d.version.toLongOption, currentVersion)
        )
    }
  }

  def validateActivationOnDescriptor(eservice: EService, descriptorId: UUID): Future[EService] = {
    val allowedStatus: List[EServiceDescriptorState] = List(EServiceDescriptorState.PUBLISHED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateOperationOnDescriptor(eservice: EService, descriptorId: UUID): Future[EService] = {
    val allowedStatus: List[EServiceDescriptorState] =
      List(EServiceDescriptorState.PUBLISHED, EServiceDescriptorState.DEPRECATED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateEServiceDescriptorStatus(
    eservice: EService,
    descriptorId: UUID,
    allowedStatus: List[EServiceDescriptorState]
  ): Future[EService] = {
    val descriptorStatus = eservice.descriptors.find(_.id == descriptorId).map(_.state)

    Future.fromTry(
      Either
        .cond(
          descriptorStatus.exists(status => allowedStatus.contains(status)),
          eservice,
          new RuntimeException(
            s"Descriptor ${descriptorId.toString} of Eservice ${eservice.id} has not status in ${allowedStatus
                .mkString("[", ",", "]")}"
          )
        )
        .toTry
    )
  }

  def getDescriptorAudience(eservice: EService, descriptorId: UUID): Future[Seq[String]] = {
    val audience = eservice.descriptors.find(_.id == descriptorId).map(_.audience)

    audience match {
      case Some(aud) => Future.successful[Seq[String]](aud)
      case None      =>
        Future.failed[Seq[String]](DescriptorNotFound(eservice.id.toString, descriptorId.toString))
    }
  }

  def flattenAttributes(attributes: Seq[Attribute]): Future[Seq[AttributeValue]] = {
    Future.fromTry {
      Try {
        attributes
          .flatMap(attribute => attribute.group.toSeq.flatten ++ attribute.single.toSeq)
      }
    }
  }

  def hasEserviceNewPublishedVersion(latestVersion: Option[Long], currentVersion: Option[Long]): Future[Boolean] =
    (latestVersion, currentVersion) match {
      case (Some(l), Some(c)) if l > c => Future.successful(true)
      case (Some(_), None)             => Future.successful(true)
      case _ => Future.failed[Boolean](new RuntimeException("No new versions exist for this agreement!"))
    }

  def descriptorStateToApi(state: EServiceDescriptorState): AgreementProcess.EServiceDescriptorState =
    state match {
      case EServiceDescriptorState.DRAFT      => AgreementProcess.EServiceDescriptorState.DRAFT
      case EServiceDescriptorState.PUBLISHED  => AgreementProcess.EServiceDescriptorState.PUBLISHED
      case EServiceDescriptorState.DEPRECATED => AgreementProcess.EServiceDescriptorState.DEPRECATED
      case EServiceDescriptorState.SUSPENDED  => AgreementProcess.EServiceDescriptorState.SUSPENDED
      case EServiceDescriptorState.ARCHIVED   => AgreementProcess.EServiceDescriptorState.ARCHIVED
    }
}
