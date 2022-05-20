package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.service.{
  PartyManagementApiKeyValue,
  PartyManagementInvoker,
  PartyManagementService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getUidFuture
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi
import it.pagopa.interop.selfcare.partymanagement.client.model.{Attribute, Institution}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, partyApi: PartyApi)(implicit
  partyManagementApiKeyValue: PartyManagementApiKeyValue
) extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getPartyAttributes(
    partyId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]] =
    for {
      uid <- getUidFuture(contexts)
      request = partyApi.getPartyAttributes(partyId)(uid)
      result <- invoker.invoke(request, s"Retrieving Attributes of party $partyId")
    } yield result

  override def getInstitution(
    partyId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = for {
    uid <- getUidFuture(contexts)
    request = partyApi.getInstitutionById(partyId)(uid)
    result <- invoker.invoke(request, s"Retrieving Institution of party $partyId")
  } yield result

}
