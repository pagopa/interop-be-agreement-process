package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorEnums.Status.{
  Deprecated,
  Published
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  Attribute,
  AttributeValue,
  EService,
  EServiceDescriptorEnums
}

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

trait CatalogManagementService {

  def getEServiceById(bearerToken: String, eServiceId: UUID): Future[EService]

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

  def validateActivationOnDescriptor(eservice: EService, descriptorId: UUID): Future[EService] = {
    val allowedStatus: List[EServiceDescriptorEnums.Status.Value] = List(Published)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateOperationOnDescriptor(eservice: EService, descriptorId: UUID): Future[EService] = {
    val allowedStatus: List[EServiceDescriptorEnums.Status.Value] = List(Published, Deprecated)
    validateEServiceDescriptorStatus(eservice, descriptorId, allowedStatus)
  }

  def validateEServiceDescriptorStatus(
    eservice: EService,
    descriptorId: UUID,
    allowedStatus: List[EServiceDescriptorEnums.Status.Value]
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

  def flattenAttributes(attributes: Seq[Attribute]): Future[Seq[AttributeValue]] = {
    Future.fromTry {
      Try {
        attributes
          .flatMap(attribute => attribute.group.toSeq.flatten ++ attribute.single.toSeq)
      }
    }
  }

}
