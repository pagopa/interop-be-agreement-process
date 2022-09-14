package it.pagopa.interop.agreementprocess.server.impl

import cats.syntax.all._
import buildinfo.BuildInfo
import akka.http.scaladsl.Http

import akka.management.scaladsl.AkkaManagement
import it.pagopa.interop.commons.utils.CORSSupport
import it.pagopa.interop.agreementprocess.server.Controller
import it.pagopa.interop.commons.logging.renderBuildInfo

import com.typesafe.scalalogging.Logger
import scala.util.{Failure, Success}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import akka.actor.typed.DispatcherSelector

object Main extends App with CORSSupport with Dependencies {

  private val logger: Logger = Logger(this.getClass)

  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[_]        = context.system
      implicit val executionContext: ExecutionContext = actorSystem.executionContext

      val selector: DispatcherSelector         = DispatcherSelector.fromConfig("futures-dispatcher")
      val blockingEc: ExecutionContextExecutor = actorSystem.dispatchers.lookup(selector)

      AkkaManagement.get(actorSystem.classicSystem).start()

      logger.info(renderBuildInfo(BuildInfo))

      val serverBinding = for {
        jwtReader <- getJwtValidator()
        agreement  = agreementApi(jwtReader, blockingEc)
        controller = new Controller(agreement, healthApi, validationExceptionToRoute.some)(actorSystem.classicSystem)
        binding <- Http()(actorSystem.classicSystem)
          .newServerAt("0.0.0.0", ApplicationConfiguration.serverPort)
          .bind(corsHandler(controller.routes))
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString}:${b.localAddress.getPort}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )

}
