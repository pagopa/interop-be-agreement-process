package it.pagopa.interop.agreementprocess.server.impl

import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl._
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.partymanagement.client.api.PartyApi
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import scala.concurrent.ExecutionContext
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{KID, PublicKeysHolder, SerializedKey}

trait Dependencies {

  implicit val actorSystem: akka.actor.typed.ActorSystem[Nothing]
  implicit lazy val ec: ExecutionContext = actorSystem.executionContext

  private lazy val agreementManagementInvoker: AgreementManagementInvoker =
    AgreementManagementInvoker()(actorSystem.toClassic)

  private lazy val agreementManagementApi: AgreementManagementApi = AgreementManagementApi(
    ApplicationConfiguration.agreementManagementURL
  )

  lazy val agreementManagement: AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker, agreementManagementApi)

  private lazy val catalogManagementInvoker: CatalogManagementInvoker =
    CatalogManagementInvoker()(actorSystem.toClassic)
  private lazy val catalogApi: EServiceApi             = EServiceApi(ApplicationConfiguration.catalogManagementURL)
  lazy val catalogManagement: CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
  def catalogManagement(catalogApi: EServiceApi): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)

  private lazy val partyManagementInvoker: PartyManagementInvoker = PartyManagementInvoker()(actorSystem.toClassic)
  private lazy val partyApi: PartyApi              = PartyApi(ApplicationConfiguration.partyManagementURL)
  lazy val partyManagement: PartyManagementService = PartyManagementServiceImpl(partyManagementInvoker, partyApi)

  private lazy val attributeRegistryManagementInvoker: AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker()(actorSystem.toClassic)
  private lazy val attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
  lazy val attributeRegistryManagement: AttributeManagementService =
    AttributeManagementServiceImpl(attributeRegistryManagementInvoker, attributeApi)

  private lazy val authorizationManagementInvoker: AuthorizationManagementInvoker =
    AuthorizationManagementInvoker()(actorSystem.toClassic)
  private lazy val authorizationPurposeApi: AuthorizationManagementPurposeApi = new AuthorizationManagementPurposeApi(
    ApplicationConfiguration.authorizationManagementURL
  )
  lazy val authorizationManagement: AuthorizationManagementService            =
    AuthorizationManagementServiceImpl(authorizationManagementInvoker, authorizationPurposeApi)

  def jwtReader(keyset: Map[KID, SerializedKey]): JWTReader = new DefaultJWTReader with PublicKeysHolder {
    var publicKeyset: Map[KID, SerializedKey] = keyset

    override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
      getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
  }

}
