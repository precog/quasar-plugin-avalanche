ThisBuild / crossScalaVersions := Seq("2.12.11")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.head

ThisBuild / githubRepository := "quasar-plugin-avalanche"

ThisBuild / homepage := Some(url("https://github.com/precog/quasar-plugin-avalanche"))

ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/precog/quasar-plugin-avalanche"),
  "scm:git@github.com:precog/quasar-plugin-avalanche.git"))

ThisBuild / publishAsOSSProject := true

lazy val quasarVersion =
  Def.setting[String](managedVersions.value("precog-quasar"))

lazy val quasarPluginJdbcVersion =
  Def.setting[String](managedVersions.value("precog-quasar-plugin-jdbc"))

val Specs2Version = "4.9.4"

// Include to also publish a project's tests
lazy val publishTestsSettings = Seq(
  Test / packageBin / publishArtifact := true)

lazy val root = project
  .in(file("."))
  .settings(noPublishSettings)
  .aggregate(core, datasource)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "quasar-plugin-avalanche",

    libraryDependencies ++= Seq(
      "com.precog" %% "quasar-plugin-jdbc" % quasarPluginJdbcVersion.value,
      "org.specs2" %% "specs2-core" % Specs2Version % Test
    ),

    // FIXME: how do we include this without assembly?
    // Assemble a "fat" jar consisting of the plugin and the unmanaged iijdbc.jar
    assemblyExcludedJars in assembly := {
      val cp = (fullClasspath in assembly).value
      cp.filter(_.data.getName != "iijdbc.jar")
    },
    packageBin in Compile := (assembly in Compile).value)

lazy val datasource = project
  .in(file("datasource"))
  .dependsOn(core)
  .settings(
    name := "quasar-datasource-avalanche",

    initialCommands in console := """
      import slamdata.Predef._

      import doobie._
      import doobie.implicits._
      import doobie.util.ExecutionContexts

      import cats._
      import cats.data._
      import cats.effect._
      import cats.implicits._

      implicit val contextShiftIO = IO.contextShift(ExecutionContexts.synchronous)

      val syncBlocker = Blocker.liftExecutionContext(ExecutionContexts.synchronous)
    """,

    quasarPluginName := "avalanche",
    quasarPluginQuasarVersion := quasarVersion.value,
    quasarPluginDatasourceFqcn := Some("quasar.plugin.avalanche.datasource.AvalancheDatasourceModule$"),

    quasarPluginDependencies ++= Seq(
      "com.precog" %% "quasar-plugin-jdbc" % quasarPluginJdbcVersion.value
    ),

    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % Specs2Version % Test
    ))
  .enablePlugins(QuasarPlugin)
