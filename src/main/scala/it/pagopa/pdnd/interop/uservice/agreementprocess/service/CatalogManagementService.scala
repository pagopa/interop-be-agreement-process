package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorEnums.Status.Published
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, AttributeValue, EService}

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
  def checkEServiceActivation(eservice: EService): Future[EService] = {
    Future.fromTry(
      Either
        .cond(
          eservice.descriptors.exists(_.status == Published),
          eservice,
          new RuntimeException(s"Eservice ${eservice.id} does not contain published versions")
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

  def verifyProducerMatch(eserviceProducerId: UUID, seedProducerId: UUID): Future[Boolean] = {
    Future.fromTry(
      Either
        .cond(
          eserviceProducerId.toString == seedProducerId.toString,
          true,
          new RuntimeException(
            s"Actual e-service producer is different from the producer passed in the request, i.e.: ${seedProducerId.toString}"
          )
        )
        .toTry
    )
  }
}
