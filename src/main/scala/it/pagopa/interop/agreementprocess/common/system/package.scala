package it.pagopa.interop.agreementprocess.common

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorSystem, Scheduler}
import akka.util.Timeout
import akka.{actor => classic}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.BearerNotFound
import it.pagopa.interop.commons.utils.BEARER
import it.pagopa.interop.commons.utils.errors.ComponentError

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

package object system {

  implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "interop-be-agreement-process")

  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  implicit val classicActorSystem: classic.ActorSystem = actorSystem.toClassic

  implicit val timeout: Timeout = 3.seconds

  implicit val scheduler: Scheduler = actorSystem.scheduler

  // TODO this may contain invalid header names, e.g.: callerIP and bearer
  def gettingHeaders(contexts: Seq[(String, String)]): Map[String, String] =
    contexts.toMap

  def getBearerHeader(headers: Map[String, String]): Either[ComponentError, String] =
    headers.get(BEARER).toRight(BearerNotFound)
}
