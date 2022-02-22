package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.actor.ActorSystem
import it.pagopa.pdnd.interop.uservice._
import it.pagopa.interop._

package object service {
  type AgreementManagementInvoker         = agreementmanagement.client.invoker.ApiInvoker
  type PartyManagementInvoker             = partymanagement.client.invoker.ApiInvoker
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker     = authorizationmanagement.client.invoker.ApiInvoker

  type AgreementManagementApi            = agreementmanagement.client.api.AgreementApi
  type AuthorizationManagementPurposeApi = authorizationmanagement.client.api.PurposeApi

  type ClientAttribute = attributeregistrymanagement.client.model.Attribute

  object PartyManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyManagementInvoker =
      partymanagement.client.invoker.ApiInvoker(partymanagement.client.api.EnumsSerializers.all)
  }

  object AgreementManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all)
  }

  object AuthorizationManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AuthorizationManagementInvoker =
      authorizationmanagement.client.invoker.ApiInvoker(authorizationmanagement.client.api.EnumsSerializers.all)
  }

  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all)
  }

  object AttributeRegistryManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker.ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all)
  }

  object AgreementManagementApi {
    def apply(baseUrl: String): AgreementManagementApi = agreementmanagement.client.api.AgreementApi(baseUrl)
  }

}
