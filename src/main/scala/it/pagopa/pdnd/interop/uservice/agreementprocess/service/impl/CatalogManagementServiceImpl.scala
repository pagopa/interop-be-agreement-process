package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, EService}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EServiceDescriptorEnums.Status.Published
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.ToString"
  )
)
final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getEServiceById(bearerToken: String, eserviceId: String): Future[EService] = {
    val request: ApiRequest[EService] = api.getEService(eserviceId)(BearerToken(bearerToken))
    invoker
      .execute[EService](request)
      .map { x =>
        logger.info(s"Retrieving e-service ${x.code}")
        logger.info(s"Retrieving e-service ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving e-service ${ex.getMessage}")
        Future.failed[EService](ex)
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  override def checkEServiceActivation(eservice: EService): Future[EService] = {
    Future.fromTry(
      Either
        .cond(
          eservice.descriptors.find(_.status == Published).nonEmpty,
          eservice,
          new RuntimeException(s"Eservice ${eservice.id} does not contain published versions")
        )
        .toTry
    )
  }

  override def flattenAttributes(verified: Seq[Attribute]): Future[Seq[UUID]] = {
    Future.fromTry {
      Try {
        verified
          .flatMap(attribute => attribute.group.toList.flatten ++ attribute.simple.toList)
          .map(UUID.fromString)
      }
    }
  }

  override def verifyProducerMatch(eserviceProducerId: UUID, seedProducerId: UUID): Future[Boolean] = {
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
