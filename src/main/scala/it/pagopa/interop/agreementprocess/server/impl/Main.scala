package it.pagopa.interop.agreementprocess.server.impl

import buildinfo.BuildInfo
import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.JWTConfiguration
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
import it.pagopa.interop.agreementprocess.server.Controller
import it.pagopa.interop.commons.logging.renderBuildInfo

import kamon.Kamon
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration

case object StartupErrorShutdown extends CoordinatedShutdown.Reason

object Main extends App with CORSSupport with Dependencies {

  private val logger: Logger = Logger(this.getClass)

  override implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "interop-be-agreement-process")

  val jwtReader: Future[JWTReader] = JWTConfiguration.jwtReader.loadKeyset().toFuture.map(createJwtReader)

  jwtReader.flatMap(launchApp).onComplete {
    case Success(binding) =>
      logger.info(renderBuildInfo(BuildInfo))
      logger.info(s"Started server at ${binding.localAddress.getHostString()}:${binding.localAddress.getPort()}")

    case Failure(ex) =>
      actorSystem.terminate()
      Kamon.stop()
      logger.error("Startup error: ", ex)
  }

  private def launchApp(jwtReader: JWTReader): Future[Http.ServerBinding] = {
    Kamon.init()
    AkkaManagement.get(actorSystem.toClassic).start()

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
    )(actorSystem.toClassic)

    Http()(actorSystem.toClassic)
      .newServerAt("0.0.0.0", ApplicationConfiguration.serverPort)
      .bind(corsHandler(controller.routes))
  }
}
