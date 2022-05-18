package it.pagopa.interop.agreementprocess.server.impl

import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl._
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi

import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{KID, PublicKeysHolder, SerializedKey}
import akka.actor.typed.ActorSystem
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import it.pagopa.interop.commons.jwt.JWTConfiguration
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.agreementprocess.api.HealthApi
import it.pagopa.interop.agreementprocess.api.impl.HealthServiceApiImpl
import it.pagopa.interop.agreementprocess.api.impl.HealthApiMarshallerImpl
import it.pagopa.interop.commons.utils.AkkaUtils
import akka.http.scaladsl.server.directives.SecurityDirectives
import it.pagopa.interop.agreementprocess.api._
import it.pagopa.interop.agreementprocess.api.impl._
import com.atlassian.oai.validator.report.ValidationReport
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import akka.http.scaladsl.server.Directives.complete

trait Dependencies {

  implicit val partyManagementApiKeyValue: PartyManagementApiKeyValue = PartyManagementApiKeyValue()

  def agreementManagement()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AgreementManagementService =
    AgreementManagementServiceImpl(
      AgreementManagementInvoker()(actorSystem.classicSystem),
      AgreementManagementApi(ApplicationConfiguration.agreementManagementURL)
    )

  def catalogManagement()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): CatalogManagementService =
    CatalogManagementServiceImpl(
      CatalogManagementInvoker()(actorSystem.classicSystem),
      EServiceApi(ApplicationConfiguration.catalogManagementURL)
    )

  def partyManagement(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): PartyManagementService =
    PartyManagementServiceImpl(
      PartyManagementInvoker()(actorSystem.classicSystem),
      PartyApi(ApplicationConfiguration.partyManagementURL)
    )

  def attributeRegistryManagement()(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AttributeManagementService =
    AttributeManagementServiceImpl(
      AttributeRegistryManagementInvoker()(actorSystem.classicSystem),
      AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
    )

  def authorizationManagement(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AuthorizationManagementService =
    AuthorizationManagementServiceImpl(
      AuthorizationManagementInvoker()(actorSystem.classicSystem),
      new AuthorizationManagementPurposeApi(ApplicationConfiguration.authorizationManagementURL)
    )

  def getJwtValidator()(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey] = keyset

        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  def agreementApi(jwtReader: JWTReader)(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AgreementApi =
    new AgreementApi(
      AgreementApiServiceImpl(
        agreementManagement(),
        catalogManagement(),
        partyManagement,
        attributeRegistryManagement(),
        authorizationManagement,
        jwtReader
      ),
      new AgreementApiMarshallerImpl(),
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  def consumerApi(jwtReader: JWTReader)(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): ConsumerApi =
    new ConsumerApi(
      ConsumerApiServiceImpl(
        agreementManagement(),
        catalogManagement(),
        partyManagement,
        attributeRegistryManagement(),
        jwtReader
      ),
      new ConsumerApiMarshallerImpl(),
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      problemOf(
        StatusCodes.BadRequest,
        GenericComponentErrors.ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
      )
    complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
  }

}
