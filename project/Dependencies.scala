import Versions._
import sbt._

object Dependencies {

  private[this] object akka {
    lazy val namespace   = "com.typesafe.akka"
    lazy val actorTyped  = namespace                       %% "akka-actor-typed"         % akkaVersion
    lazy val actor       = namespace                       %% "akka-actor"               % akkaVersion
    lazy val persistence = namespace                       %% "akka-persistence-typed"   % akkaVersion
    lazy val stream      = namespace                       %% "akka-stream"              % akkaVersion
    lazy val http        = namespace                       %% "akka-http"                % akkaHttpVersion
    lazy val httpJson    = namespace                       %% "akka-http-spray-json"     % akkaHttpVersion
    lazy val httpJson4s  = "de.heikoseeberger"             %% "akka-http-json4s"         % "1.37.0"
    lazy val management  = "com.lightbend.akka.management" %% "akka-management"          % "1.1.1"
    lazy val slf4j       = namespace                       %% "akka-slf4j"               % akkaVersion
    lazy val testkit     = namespace                       %% "akka-actor-testkit-typed" % akkaVersion
  }

  private[this] object pagopa {
    lazy val namespace = "it.pagopa"
    lazy val agreementManagementClient =
      namespace %% "pdnd-interop-uservice-agreement-management-client" % agreementManagementVersion
    lazy val catalogManagementClient =
      namespace %% "pdnd-interop-uservice-catalog-management-client" % catalogManagementVersion
    lazy val partyManagementClient =
      namespace %% "pdnd-interop-uservice-party-management-client" % partyManagementVersion
  }

  private[this] object json4s {
    lazy val namespace = "org.json4s"
    lazy val jackson   = namespace %% "json4s-jackson" % json4sVersion
    lazy val ext       = namespace %% "json4s-ext"     % json4sVersion
  }

  private[this] object jackson {
    lazy val namespace   = "com.fasterxml.jackson.core"
    lazy val core        = namespace % "jackson-core"        % jacksonVersion
    lazy val annotations = namespace % "jackson-annotations" % jacksonVersion
    lazy val databind    = namespace % "jackson-databind"    % jacksonVersion
  }

  private[this] object logback {
    lazy val namespace = "ch.qos.logback"
    lazy val classic   = namespace % "logback-classic" % logbackVersion
  }

  private[this] object kamon {
    lazy val namespace  = "io.kamon"
    lazy val bundle     = namespace %% "kamon-bundle"     % kamonVersion
    lazy val prometheus = namespace %% "kamon-prometheus" % kamonVersion
  }

  private[this] object openapi4j {
    lazy val namespace          = "org.openapi4j"
    lazy val operationValidator = namespace % "openapi-operation-validator" % openapi4jVersion
  }

  private[this] object scalatest {
    lazy val namespace = "org.scalatest"
    lazy val core      = namespace %% "scalatest" % scalatestVersion
  }

  private[this] object mockito {
    lazy val namespace = "org.mockito"
    lazy val core      = namespace % "mockito-core" % mockitoVersion
  }

  private[this] object scalamock {
    lazy val namespace = "org.scalamock"
    lazy val core      = namespace %% "scalamock" % scalaMockVersion
  }

  private[this] object scalapact {
    lazy val namespace = "com.itv"
    lazy val core      = namespace %% "scalapact-scalatest-suite" % scalaPactVersion
  }

  object Jars {
    lazy val overrides: Seq[ModuleID] =
      Seq(jackson.annotations % Compile, jackson.core % Compile, jackson.databind % Compile)
    lazy val `server`: Seq[ModuleID] = Seq(
      // For making Java 12 happy
      "javax.annotation" % "javax.annotation-api" % "1.3.2" % "compile",
      //
      akka.actorTyped                  % Compile,
      akka.actor                       % Compile,
      akka.persistence                 % Compile,
      akka.management                  % Compile,
      akka.stream                      % Compile,
      akka.http                        % Compile,
      akka.httpJson                    % Compile,
      logback.classic                  % Compile,
      akka.slf4j                       % Compile,
      openapi4j.operationValidator     % Compile,
      kamon.bundle                     % Compile,
      kamon.prometheus                 % Compile,
      pagopa.agreementManagementClient % Compile,
      pagopa.catalogManagementClient   % Compile,
      pagopa.partyManagementClient     % Compile,
      scalatest.core                   % Test,
      mockito.core                     % Test,
      scalamock.core                   % Test,
      akka.testkit                     % Test,
      scalapact.core                   % Test
    )
    lazy val client: Seq[ModuleID] = Seq(
      akka.stream     % Compile,
      akka.http       % Compile,
      akka.httpJson4s % Compile,
      json4s.jackson  % Compile,
      json4s.ext      % Compile
    )
  }
}
