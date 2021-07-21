package it.pagopa.pdnd.interop.uservice.agreementprocess

import akka.actor.ActorSystem
import it.pagopa.pdnd.interop.uservice._

package object service {
  type AgreementManagementInvoker = agreementmanagement.client.invoker.ApiInvoker
  type CatalogManagementInvoker   = catalogmanagement.client.invoker.ApiInvoker

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object AgreementManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all)
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all)
  }
}
