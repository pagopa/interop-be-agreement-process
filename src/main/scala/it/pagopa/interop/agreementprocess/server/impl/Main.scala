package it.pagopa.interop.agreementprocess.server.impl

import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.{CORSSupport, OpenapiUtils}
import it.pagopa.interop.agreementprocess.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  ConsumerApiMarshallerImpl,
  ConsumerApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  problemOf
}
import it.pagopa.interop.agreementprocess.api.{AgreementApi, ConsumerApi, HealthApi}
import it.pagopa.interop.agreementprocess.common.system.{ApplicationConfiguration, classicActorSystem, executionContext}
import it.pagopa.interop.agreementprocess.server.Controller
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl.{
  AgreementManagementServiceImpl,
  AttributeManagementServiceImpl,
  AuthorizationManagementServiceImpl,
  CatalogManagementServiceImpl,
  PartyManagementServiceImpl
}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.partymanagement.client.api.PartyApi
import kamon.Kamon
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait AgreementManagementDependency {
  private final val agreementManagementInvoker: AgreementManagementInvoker = AgreementManagementInvoker()
  private final val agreementManagementApi: AgreementManagementApi         = AgreementManagementApi(
    ApplicationConfiguration.agreementManagementURL
  )

  val agreementManagement: AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker, agreementManagementApi)
}

trait CatalogManagementDependency {
  private final val catalogManagementInvoker: CatalogManagementInvoker = CatalogManagementInvoker()
  private final val catalogApi: EServiceApi       = EServiceApi(ApplicationConfiguration.catalogManagementURL)
  val catalogManagement: CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
  def catalogManagement(catalogApi: EServiceApi): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
}

trait PartyManagementDependency {
  private final val partyManagementInvoker: PartyManagementInvoker = PartyManagementInvoker()
  private final val partyApi: PartyApi        = PartyApi(ApplicationConfiguration.partyManagementURL)
  val partyManagement: PartyManagementService =
    PartyManagementServiceImpl(partyManagementInvoker, partyApi)
}

trait AttributeRegistryManagementDependency {
  private final val attributeRegistryManagementInvoker: AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker()
  private final val attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
  val attributeRegistryManagement: AttributeManagementService =
    AttributeManagementServiceImpl(attributeRegistryManagementInvoker, attributeApi)
}

trait AuthorizationManagementDependency {
  private final val authorizationManagementInvoker: AuthorizationManagementInvoker = AuthorizationManagementInvoker()
  private final val authorizationPurposeApi: AuthorizationManagementPurposeApi     =
    new AuthorizationManagementPurposeApi(ApplicationConfiguration.authorizationManagementURL)
  val authorizationManagement: AuthorizationManagementService                      =
    AuthorizationManagementServiceImpl(authorizationManagementInvoker, authorizationPurposeApi)
}

//shuts down the actor system in case of startup errors
case object StartupErrorShutdown extends CoordinatedShutdown.Reason

object Main
    extends App
    with CORSSupport
    with AgreementManagementDependency
    with CatalogManagementDependency
    with PartyManagementDependency
    with AttributeRegistryManagementDependency
    with AuthorizationManagementDependency {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val dependenciesLoaded: Future[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset().toFuture
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey]                                        = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield jwtValidator

  dependenciesLoaded.transformWith {
    case Success(jwtValidator) => launchApp(jwtValidator)
    case Failure(ex)           =>
      logger.error(s"Startup error - ${ex.getMessage}")
      logger.error(ex.getStackTrace.mkString("\n"))
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
  }

  private def launchApp(jwtReader: JWTReader): Future[Http.ServerBinding] = {
    Kamon.init()

    val agreementApi: AgreementApi = new AgreementApi(
      AgreementApiServiceImpl(
        agreementManagement,
        catalogManagement,
        partyManagement,
        attributeRegistryManagement,
        authorizationManagement,
        jwtReader
      ),
      new AgreementApiMarshallerImpl(),
      jwtReader.OAuth2JWTValidatorAsContexts
    )

    val consumerApi: ConsumerApi = new ConsumerApi(
      ConsumerApiServiceImpl(
        agreementManagement,
        catalogManagement,
        partyManagement,
        attributeRegistryManagement,
        jwtReader
      ),
      new ConsumerApiMarshallerImpl(),
      jwtReader.OAuth2JWTValidatorAsContexts
    )

    val healthApi: HealthApi = new HealthApi(
      new HealthServiceApiImpl(),
      HealthApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )

    locally {
      val _ = AkkaManagement.get(classicActorSystem).start()
    }

    val controller: Controller = new Controller(
      health = healthApi,
      agreement = agreementApi,
      consumer = consumerApi,
      validationExceptionToRoute = Some(report => {
        val error =
          problemOf(
            StatusCodes.BadRequest,
            ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
          )
        complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
      })
    )

    logger.info(s"Started build info = ${buildinfo.BuildInfo.toString}")

    val bindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))
    bindingFuture
  }
}
