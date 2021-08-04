import sbt.Keys.{parallelExecution, testOptions}
import sbt.{Def, Defaults, Project, State, Test, Tests, addCommandAlias, config, inConfig}

object ProjectSettings {

  def addContractTestCommandAlias: Seq[Def.Setting[State => State]] = {
    addCommandAlias("contract-test", "ContractTest / test")
  }

  implicit class ProjectFrom(project: Project) {
    private def contractFilter(name: String): Boolean = name endsWith "ContractSpec"
    private def unitFilter(name: String): Boolean     = !contractFilter(name)

    def enableContractTest: Project = {
      lazy val ContractTest = config("contract") extend (Test)

      lazy val testSettings: Seq[Def.Setting[_]] =
        inConfig(ContractTest)(Defaults.testTasks) ++ Seq(
          Test / testOptions := Seq(Tests.Filter(unitFilter)),
          ContractTest / testOptions := Seq(Tests.Filter(contractFilter)),
          Test / parallelExecution := false,
          ContractTest / parallelExecution := false
        )

      project
        .configs(ContractTest)
        .settings(testSettings)
    }

  }

}
