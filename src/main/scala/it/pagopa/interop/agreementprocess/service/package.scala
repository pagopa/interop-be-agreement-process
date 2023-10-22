package it.pagopa.interop.agreementprocess

import akka.actor.ActorSystem
import it.pagopa.interop._
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration

import scala.concurrent.ExecutionContextExecutor

package object service {
  type AgreementManagementInvoker     = agreementmanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker = authorizationmanagement.client.invoker.ApiInvoker
  type SelfcareV2ClientInvoker        = selfcare.v2.client.invoker.ApiInvoker

  type AgreementManagementApi = agreementmanagement.client.api.AgreementApi

  type SelfcareV2ClientApiKeyValue = selfcare.v2.client.invoker.ApiKeyValue
  object SelfcareV2ClientApiKeyValue {
    def apply(): SelfcareV2ClientApiKeyValue =
      selfcare.v2.client.invoker.ApiKeyValue(ApplicationConfiguration.selfcareV2ClientApiKey)
  }
  type ClientAttribute = attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

  object AgreementManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object AuthorizationManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AuthorizationManagementInvoker =
      authorizationmanagement.client.invoker
        .ApiInvoker(authorizationmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object SelfcareV2ClientInvoker {
    def apply()(implicit actorSystem: ActorSystem): SelfcareV2ClientInvoker =
      selfcare.v2.client.invoker.ApiInvoker(selfcare.v2.client.api.EnumsSerializers.all)
  }

  object AgreementManagementApi {
    def apply(baseUrl: String): AgreementManagementApi = agreementmanagement.client.api.AgreementApi(baseUrl)
  }
}
