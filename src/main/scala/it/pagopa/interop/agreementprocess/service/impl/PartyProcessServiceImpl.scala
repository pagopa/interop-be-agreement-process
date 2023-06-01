package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.service.{PartyProcessInvoker, PartyProcessService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.commons.utils.withUid
import it.pagopa.interop.selfcare.partyprocess.client.api.ProcessApi
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError, ApiKeyValue}
import it.pagopa.interop.selfcare.partyprocess.client.model.Institution

import scala.concurrent.{ExecutionContext, Future}

final class PartyProcessServiceImpl(invoker: PartyProcessInvoker, api: ProcessApi) extends PartyProcessService {

  implicit val partyProcessApiKeyValue: ApiKeyValue = ApiKeyValue(ApplicationConfiguration.partyProcessApiKey)

  private val replacementEntityId: String = "NoIdentifier"
  private val serviceName: String         = "party-process"

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    selfcareId: String
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = withUid { uid =>
    selfcareId.toFutureUUID.flatMap { selfcareUUID =>
      val request = api.getInstitution(selfcareUUID)(uid)
      invoker.invoke(request, s"Institution ${selfcareId}", invocationRecovery(None))
    }
  }

  private def invocationRecovery[T](
    entityId: Option[String]
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, msg) => {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ResourceNotFoundError(entityId.getOrElse(replacementEntityId)))
      case ex @ ApiError(code, message, _, _, _)                =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ThirdPartyCallError(serviceName, ex.getMessage))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)(context)
        Future.failed[T](GenericClientError(ex.getMessage))
    }

}
