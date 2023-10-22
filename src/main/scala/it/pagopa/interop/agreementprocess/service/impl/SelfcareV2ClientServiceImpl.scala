package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.service.{
  SelfcareV2ClientInvoker,
  SelfcareV2ClientApiKeyValue,
  SelfcareV2ClientService
}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{InstitutionNotFound, UserNotFound}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.v2.client.api.{InstitutionsApi, UsersApi}
import it.pagopa.interop.selfcare.v2.client.invoker.{ApiError, ApiRequest}
import it.pagopa.interop.selfcare.v2.client.model.{Institution, UserResponse}
import cats.syntax.all._

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

final class SelfcareV2ClientServiceImpl(
  invoker: SelfcareV2ClientInvoker,
  institutionsApi: InstitutionsApi,
  usersApi: UsersApi
)(implicit selfcareClientApiKeyValue: SelfcareV2ClientApiKeyValue)
    extends SelfcareV2ClientService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    selfcareId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = {
    val request: ApiRequest[Institution] = institutionsApi.getInstitution(selfcareId)
    invoker
      .invoke(request, s"Institution ${selfcareId}")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(InstitutionNotFound(selfcareId))
      }
  }

  override def getUserById(selfcareId: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[UserResponse] = {
    val request: ApiRequest[UserResponse] =
      usersApi.getUserInfoUsingGET(id = userId.toString, institutionId = selfcareId.toString.some)
    invoker
      .invoke(request, s"Retrieving User with with istitution id $selfcareId, user $userId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(UserNotFound(selfcareId, userId))
      }
  }
}
