package it.pagopa.interop.agreementprocess

import akka.actor.ActorSystem
import it.pagopa.interop._
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.selfcare._

import scala.concurrent.ExecutionContextExecutor

package object service {
  type AgreementManagementInvoker     = agreementmanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker = authorizationmanagement.client.invoker.ApiInvoker
  type PartyProcessInvoker            = partyprocess.client.invoker.ApiInvoker
  type UserRegistryManagementInvoker  = userregistry.client.invoker.ApiInvoker

  type AgreementManagementApi = agreementmanagement.client.api.AgreementApi

  type UserRegistryApiKeyValue = selfcare.userregistry.client.invoker.ApiKeyValue
  object UserRegistryApiKeyValue {
    def apply(): UserRegistryApiKeyValue =
      userregistry.client.invoker.ApiKeyValue(ApplicationConfiguration.userRegistryApiKey)
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

  object PartyProcessServiceInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyProcessInvoker =
      partyprocess.client.invoker.ApiInvoker(partyprocess.client.api.EnumsSerializers.all)
  }

  object AgreementManagementApi {
    def apply(baseUrl: String): AgreementManagementApi = agreementmanagement.client.api.AgreementApi(baseUrl)
  }

  object UserRegistryManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): UserRegistryManagementInvoker =
      userregistry.client.invoker.ApiInvoker(userregistry.client.api.EnumsSerializers.all)
  }

}
