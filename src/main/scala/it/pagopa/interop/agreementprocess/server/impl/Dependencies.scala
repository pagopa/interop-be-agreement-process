package it.pagopa.interop.agreementprocess.server.impl

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.agreementprocess.api._
import it.pagopa.interop.agreementprocess.api.impl.{HealthApiMarshallerImpl, HealthServiceApiImpl, _}
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.error.Handlers.serviceCode
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl._
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.{Problem => CommonProblem}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
import it.pagopa.interop.selfcare.userregistry.client.api.UserApi
import it.pagopa.interop.tenantmanagement.client.api.TenantApi

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val userRegistryApiKeyValue: UserRegistryApiKeyValue = UserRegistryApiKeyValue()

  def agreementManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AgreementManagementService =
    new AgreementManagementServiceImpl(
      AgreementManagementInvoker(blockingEc)(actorSystem.classicSystem),
      AgreementManagementApi(ApplicationConfiguration.agreementManagementURL)
    )

  def catalogManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): CatalogManagementService =
    new CatalogManagementServiceImpl(
      CatalogManagementInvoker(blockingEc)(actorSystem.classicSystem),
      EServiceApi(ApplicationConfiguration.catalogManagementURL)
    )

  def tenantManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_]): TenantManagementService =
    new TenantManagementServiceImpl(
      TenantManagementInvoker(blockingEc)(actorSystem.classicSystem),
      TenantApi(ApplicationConfiguration.tenantManagementURL)
    )(blockingEc)

  def attributeRegistryManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AttributeManagementService =
    new AttributeManagementServiceImpl(
      AttributeRegistryManagementInvoker(blockingEc)(actorSystem.classicSystem),
      AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
    )

  def authorizationManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AuthorizationManagementService =
    new AuthorizationManagementServiceImpl(
      AuthorizationManagementInvoker(blockingEc)(actorSystem.classicSystem),
      new PurposeApi(ApplicationConfiguration.authorizationManagementURL)
    )

  def userRegistry(implicit actorSystem: ActorSystem[_]): UserRegistryService =
    new UserRegistryServiceImpl(
      UserRegistryManagementInvoker()(actorSystem.classicSystem),
      UserApi(ApplicationConfiguration.userRegistryURL)
    )

  def getJwtValidator(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey] = keyset

        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  def fileManager(blockingEc: ExecutionContextExecutor): FileManager =
    FileManager.get(ApplicationConfiguration.storageKind match {
      case "S3"   => FileManager.S3
      case "file" => FileManager.File
      case _      => throw new Exception("Incorrect File Manager")
    })(blockingEc)

  def agreementApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AgreementApi =
    new AgreementApi(
      AgreementApiServiceImpl(
        agreementManagement(blockingEc),
        catalogManagement(blockingEc),
        tenantManagement(blockingEc),
        attributeRegistryManagement(blockingEc),
        authorizationManagement(blockingEc),
        userRegistry,
        PDFCreator,
        fileManager(blockingEc),
        OffsetDateTimeSupplier,
        UUIDSupplier
      ),
      AgreementApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator),
    loggingEnabled = false
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      CommonProblem(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report), serviceCode)
    complete(error.status, error)
  }
}
