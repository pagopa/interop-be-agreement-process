package it.pagopa.pdnd.interop.uservice.agreementprocess.common

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorSystem, Scheduler}
import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import akka.util.Timeout
import akka.{actor => classic}

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Try

package object system {

  implicit val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "pdnd-interop-uservice-agreement-process")

  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  implicit val classicActorSystem: classic.ActorSystem = actorSystem.toClassic

  implicit val timeout: Timeout = 3.seconds

  implicit val scheduler: Scheduler = actorSystem.scheduler

  object Authenticator extends Authenticator[Seq[(String, String)]] {
    override def apply(credentials: Credentials): Option[Seq[(String, String)]] = {
      credentials match {
        case Provided(identifier) => Some(Seq("bearer" -> identifier))
        case Missing              => None
      }
    }
  }

  object PassAuthenticator extends Authenticator[Seq[(String, String)]] {
    override def apply(credentials: Credentials): Option[Seq[(String, String)]] = {
      credentials match {
        case Provided(identifier) => Some(Seq("bearer" -> identifier))
        case Missing              => Some(Seq.empty[(String, String)])
      }
    }
  }

  implicit class TryOps[A](val tryOp: Try[A]) extends AnyVal {
    def toFuture: Future[A] = tryOp.fold(e => Future.failed[A](e), a => Future.successful[A](a))
  }

  implicit class EitherOps[A](val either: Either[Throwable, A]) extends AnyVal {
    def toFuture: Future[A] = either.fold(e => Future.failed[A](e), a => Future.successful[A](a))
  }

  implicit class StringOps(val str: String) extends AnyVal {
    def parseUUID: Either[Throwable, UUID] = Try { UUID.fromString(str) }.toEither
    def futureParseUUID: Future[UUID]      = Try { UUID.fromString(str) }.toFuture
  }
}
