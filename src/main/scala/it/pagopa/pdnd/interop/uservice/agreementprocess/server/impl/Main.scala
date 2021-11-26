package it.pagopa.pdnd.interop.uservice.agreementprocess.server.impl

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.{Authenticator, PassThroughAuthenticator}
import it.pagopa.pdnd.interop.commons.utils.CORSSupport
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{
  AgreementApiMarshallerImpl,
  AgreementApiServiceImpl,
  ConsumerApiMarshallerImpl,
  ConsumerApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.{AgreementApi, ConsumerApi, HealthApi}
import it.pagopa.pdnd.interop.uservice.agreementprocess.common.system.{
  ApplicationConfiguration,
  classicActorSystem,
  executionContext
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementprocess.service._
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl.{
  AgreementManagementServiceImpl,
  AttributeManagementServiceImpl,
  CatalogManagementServiceImpl,
  PartyManagementServiceImpl
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import kamon.Kamon
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

trait AgreementManagementDependency {
  private final val agreementManagementInvoker: AgreementManagementInvoker = AgreementManagementInvoker()
  private final val agreementManagementApi: AgreementManagementApi = AgreementManagementApi(
    ApplicationConfiguration.agreementManagementURL
  )

  def agreementManagement(): AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker, agreementManagementApi)
}

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
trait CatalogManagementDependency {
  private final val catalogManagementInvoker: CatalogManagementInvoker = CatalogManagementInvoker()
  private final val catalogApi: EServiceApi                            = EServiceApi(ApplicationConfiguration.catalogManagementURL)
  def catalogManagement(): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
  def catalogManagement(catalogApi: EServiceApi): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
}

trait PartyManagementDependency {
  private final val partyManagementInvoker: PartyManagementInvoker = PartyManagementInvoker()
  private final val partyApi: PartyApi                             = PartyApi(ApplicationConfiguration.partyManagementURL)
  def partyManagement(): PartyManagementService =
    PartyManagementServiceImpl(partyManagementInvoker, partyApi)
}

trait AttributeRegistryManagementDependency {
  private final val attributeRegistryManagementInvoker: AttributeRegistryManagementInvoker =
    AttributeRegistryManagementInvoker()
  private final val attributeApi: AttributeApi = AttributeApi(ApplicationConfiguration.attributeRegistryManagementURL)
  def attributeRegistryManagement(): AttributeManagementService =
    AttributeManagementServiceImpl(attributeRegistryManagementInvoker, attributeApi)
}

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny", "org.wartremover.warts.Nothing"))
object Main
    extends App
    with CORSSupport
    with AgreementManagementDependency
    with CatalogManagementDependency
    with PartyManagementDependency
    with AttributeRegistryManagementDependency {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  Kamon.init()

  final val agreementManagementService: AgreementManagementService = agreementManagement()
  final val catalogManagementService: CatalogManagementService     = catalogManagement()
  final val partyManagementService: PartyManagementService         = partyManagement()
  final val attributeManagementService: AttributeManagementService = attributeRegistryManagement()

  val agreementApi: AgreementApi = new AgreementApi(
    new AgreementApiServiceImpl(
      agreementManagementService,
      catalogManagementService,
      partyManagementService,
      attributeManagementService
    ),
    new AgreementApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)
  )

  val consumerApi: ConsumerApi = new ConsumerApi(
    new ConsumerApiServiceImpl(
      agreementManagementService,
      catalogManagementService,
      partyManagementService,
      attributeManagementService
    ),
    new ConsumerApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)
  )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    new HealthApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
  )

  locally {
    val _ = AkkaManagement.get(classicActorSystem).start()
  }

  val controller: Controller = new Controller(health = healthApi, agreement = agreementApi, consumer = consumerApi)

  logger.error(s"Started build info = ${buildinfo.BuildInfo.toString}")

  val bindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))
}
