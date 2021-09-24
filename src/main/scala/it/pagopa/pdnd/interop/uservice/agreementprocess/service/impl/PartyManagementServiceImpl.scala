package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.invoker.ApiRequest
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.Organization
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.ToString"
  )
)
final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, partyApi: PartyApi)(implicit
  ec: ExecutionContext
) extends PartyManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getPartyAttributes(bearerToken: String)(partyId: String): Future[Seq[String]] = {
    for {
      uuid       <- Future.fromTry(Try(UUID.fromString(partyId)))
      attributes <- attributesByUUID(bearerToken)(uuid)
    } yield attributes
  }

  private def attributesByUUID(bearerToken: String)(partyId: UUID): Future[Seq[String]] = {
    logger.info(s"TODO > Bearer Token should be used $bearerToken") //TODO pass bearer token
    val request: ApiRequest[Seq[String]] = partyApi.getPartyAttributes(partyId)
    invoker
      .execute[Seq[String]](request)
      .map { x =>
        logger.info(s"Retrieving attributes ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving attributes by UUID FAILED: ${ex.getMessage}")
        Future.failed[Seq[String]](ex)
      }
  }

  override def getOrganization(bearerToken: String)(partyId: UUID): Future[Organization] = {
    val request: ApiRequest[Organization] = partyApi.getPartyOrganizationByUUID(partyId)
    invoker
      .execute[Organization](request)
      .map { x =>
        logger.info(s"Retrieving Organization $partyId ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving Organization $partyId FAILED: ${ex.getMessage}")
        Future.failed[Organization](ex)
      }
  }

}
