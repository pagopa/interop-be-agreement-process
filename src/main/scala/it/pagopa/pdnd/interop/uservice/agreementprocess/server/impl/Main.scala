package it.pagopa.pdnd.interop.uservice.agreementprocess.server.impl

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  ProcessApiMarshallerImpl,
  ProcessApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.api.{HealthApi, ProcessApi}
import it.pagopa.pdnd.interop.uservice.agreementprocess.common.system.{
  ApplicationConfiguration,
  Authenticator,
  CorsSupport,
  PassAuthenticator,
  classicActorSystem,
  executionContext
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.server.Controller
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl.{
  AgreementManagementServiceImpl,
  CatalogManagementServiceImpl
}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AgreementManagementInvoker,
  AgreementManagementService,
  CatalogManagementInvoker,
  CatalogManagementService
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import kamon.Kamon

import scala.concurrent.Future

trait AgreementManagementAPI {
  private final val agreementManagementInvoker: AgreementManagementInvoker = AgreementManagementInvoker()
  private final val agreementApi: AgreementApi                             = AgreementApi(ApplicationConfiguration.agreementManagementURL)
  def agreementManagement(): AgreementManagementService =
    AgreementManagementServiceImpl(agreementManagementInvoker, agreementApi)
}

trait CatalogManagementAPI {
  private final val catalogManagementInvoker: CatalogManagementInvoker = CatalogManagementInvoker()
  private final val catalogApi: EServiceApi                            = EServiceApi(ApplicationConfiguration.catalogManagementURL)
  def catalogManagement(): CatalogManagementService =
    CatalogManagementServiceImpl(catalogManagementInvoker, catalogApi)
}

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny", "org.wartremover.warts.Nothing"))
object Main extends App with CorsSupport with AgreementManagementAPI with CatalogManagementAPI {

  Kamon.init()

  final val agreementManagementService: AgreementManagementService = agreementManagement()
  final val catalogManagementService: CatalogManagementService     = catalogManagement()

  val processApi: ProcessApi = new ProcessApi(
    new ProcessApiServiceImpl(agreementManagementService, catalogManagementService),
    new ProcessApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)
  )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    new HealthApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", PassAuthenticator)
  )

  locally {
    val _ = AkkaManagement.get(classicActorSystem).start()

  }

  val controller: Controller = new Controller(healthApi, processApi)

  val bindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))
}
