package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementprocess.error.DescriptorNotFound
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

trait CatalogManagementService {

  def getEServiceById(bearerToken: String)(eServiceId: UUID): Future[EService]

}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Option2Iterable",
    "org.wartremover.warts.ToString"
  )
)
object CatalogManagementService {
  def getActiveDescriptorOption(
    eservice: EService,
    currentDescriptor: EServiceDescriptor
  ): Future[Option[EServiceDescriptor]] = {

    Future.successful {
      val currentVersion = currentDescriptor.version.toLongOption
      eservice.descriptors
        .find(d =>
          d.status == PUBLISHED && Ordering[Option[Long]]
            .gt(d.version.toLongOption, currentVersion)
        )
    }
  }

  def validateActivationOnDescriptor(eservice: EService, descriptorId: UUID): Future[EService] = {
    val allowedStatus: List[EServiceDescriptorStatusEnum] = List(PUBLISHED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateOperationOnDescriptor(eservice: EService, descriptorId: UUID): Future[EService] = {
    val allowedStatus: List[EServiceDescriptorStatusEnum] = List(PUBLISHED, DEPRECATED)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateEServiceDescriptorStatus(
    eservice: EService,
    descriptorId: UUID,
    allowedStatus: List[EServiceDescriptorStatusEnum]
  ): Future[EService] = {
    val descriptorStatus = eservice.descriptors.find(_.id == descriptorId).map(_.status)

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
      case None =>
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

  def hasEserviceNewPublishedVersion(latestVersion: Option[Long], currentVersion: Option[Long]): Future[Boolean] = {
    (latestVersion, currentVersion) match {
      case (Some(l), Some(c)) if l > c => Future.successful(true)
      case (Some(_), None)             => Future.successful(true)
      case _                           => Future.failed[Boolean](new RuntimeException("No new versions exist for this agreement!"))
    }

  }
}
