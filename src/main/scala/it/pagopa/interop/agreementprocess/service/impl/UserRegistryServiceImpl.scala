package it.pagopa.interop.agreementprocess.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.service.{UserRegistryManagementInvoker, UserRegistryService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.userregistry.client.api.UserApi
import it.pagopa.interop.selfcare.userregistry.client.invoker.{ApiKeyValue, ApiRequest}
import it.pagopa.interop.selfcare.userregistry.client.model.{Field, UserResource}

import java.util.UUID
import scala.concurrent.Future

final class UserRegistryServiceImpl(invoker: UserRegistryManagementInvoker, api: UserApi)(implicit
  apiKeyValue: ApiKeyValue
) extends UserRegistryService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getUserById(id: UUID)(implicit contexts: Seq[(String, String)]): Future[UserResource] = {
    val request: ApiRequest[UserResource] =
      api.findByIdUsingGET(id, Seq(Field.name, Field.familyName, Field.fiscalCode))
    invoker.invoke(request, "Retrieve User by ID")
  }
}
