package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.service.{
  PartyManagementApiKeyValue,
  PartyManagementInvoker,
  PartyManagementService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withUid
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi
import it.pagopa.interop.selfcare.partymanagement.client.model.Institution

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final class PartyManagementServiceImpl(invoker: PartyManagementInvoker, partyApi: PartyApi)(implicit
  partyManagementApiKeyValue: PartyManagementApiKeyValue
) extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    partyId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = withUid { uid =>
    val request = partyApi.getInstitutionById(partyId)(uid)
    invoker.invoke(request, s"Retrieving Institution of party $partyId")
  }

}
