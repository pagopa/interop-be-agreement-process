import sbt.Keys.{parallelExecution, testOptions}
import sbt.{Def, Defaults, Project, State, Test, Tests, addCommandAlias, config, inConfig}
import sbtbuildinfo.BuildInfoKeys.buildInfoOptions
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, buildInfoKeys}
import sbtbuildinfo.{BuildInfoOption, BuildInfoPlugin}

import scala.sys.process._
import scala.util.Try

object ProjectSettings {

  def addContractTestCommandAlias: Seq[Def.Setting[State => State]] = {
    addCommandAlias("contract-test", "ContractTest / test")
  }

  implicit class ProjectFrom(project: Project) {
    private def contractFilter(name: String): Boolean = name endsWith "ContractSpec"
    private def unitFilter(name: String): Boolean     = !contractFilter(name)

    // TODO since Git 2.22 we could use the following command instead: git branch --show-current
    private val currentBranch: Option[String] = Try(
      Process(s"git rev-parse --abbrev-ref HEAD").lineStream_!.head
    ).toOption

    private val commitSha: Option[String] = Try(Process(s"git rev-parse --short HEAD").lineStream_!.head).toOption

    private val interfaceVersion: String = ComputeVersion.version match {
      case ComputeVersion.tag(major, minor, _) => s"$major.$minor"
      case _                                   => "0.0"
    }

    // lifts some useful data in BuildInfo instance
    val buildInfoExtra: Seq[BuildInfoKey] = Seq[BuildInfoKey](
      "ciBuildNumber"    -> sys.env.get("BUILD_NUMBER"),
      "commitSha"        -> commitSha,
      "currentBranch"    -> currentBranch,
      "interfaceVersion" -> interfaceVersion
    )

    def enableContractTest: Project = {
      lazy val ContractTest = config("contract") extend (Test)

      lazy val testSettings: Seq[Def.Setting[_]] =
        inConfig(ContractTest)(Defaults.testTasks) ++ Seq(
          Test / testOptions               := Seq(Tests.Filter(unitFilter)),
          ContractTest / testOptions       := Seq(Tests.Filter(contractFilter)),
          Test / parallelExecution         := false,
          ContractTest / parallelExecution := false
        )

      project
        .configs(ContractTest)
        .settings(testSettings)
    }

    def setupBuildInfo: Project = {
      project
        .enablePlugins(BuildInfoPlugin)
        .settings(buildInfoKeys ++= buildInfoExtra)
        .settings(buildInfoOptions += BuildInfoOption.BuildTime)
        .settings(buildInfoOptions += BuildInfoOption.ToJson)
    }
  }

}
