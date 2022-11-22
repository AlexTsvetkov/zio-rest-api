val zioHttpVersion        = "2.0.0-RC10"
val slickVersion          = "3.4.1"
val zioSlickInterop       = "0.5.0"
val zioVersion            = "2.0.2"
val zioLoggingVersion     = "2.1.2"
val zioConfigVersion      = "3.0.2"
val flywayVersion         = "9.4.0"
val testContainersVersion = "0.40.11"
val postgresVersion       = "42.5.0"
val zioJsonVersion        = "0.3.0-RC11"
val logbackClassicVersion = "1.2.11"
val jansiVersion          = "2.4.0"

val dockerReleaseSettings = Seq(
  dockerExposedPorts   := Seq(8080),
  dockerExposedVolumes := Seq("/opt/docker/logs"),
  dockerBaseImage      := "eclipse-temurin:17.0.4_8-jre"
)

lazy val It = config("it").extend(Test)

val root = (project in file("."))
  .configs(It)
  .settings(
    inConfig(It)(Defaults.testSettings),
    inThisBuild(
      List(
        organization := "scala.school",
        scalaVersion := "2.13.10"
      )
    ),
    name           := "zio-rest-api",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies ++= Seq(
      // zio-http
      "io.d11" %% "zhttp" % zioHttpVersion,
      // slick
      "com.typesafe.slick" %% "slick"             % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp"    % slickVersion,
      "io.scalac"          %% "zio-slick-interop" % zioSlickInterop,
      // general
      "dev.zio"        %% "zio-json"            % zioJsonVersion,
      "dev.zio"        %% "zio"                 % zioVersion,
      "dev.zio"        %% "zio-config"          % zioConfigVersion,
      "dev.zio"        %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio"        %% "zio-config-typesafe" % zioConfigVersion,
      "org.postgresql"  % "postgresql"          % postgresVersion,
      "org.flywaydb"    % "flyway-core"         % flywayVersion,

      // logging
      "dev.zio"             %% "zio-logging"       % zioLoggingVersion,
      "dev.zio"             %% "zio-logging-slf4j" % zioLoggingVersion,
      "ch.qos.logback"       % "logback-classic"   % logbackClassicVersion,
      "org.fusesource.jansi" % "jansi"             % jansiVersion,

      // test
      "dev.zio"            %% "zio-test-sbt"                    % zioVersion            % Test,
      "com.dimafeng"       %% "testcontainers-scala-postgresql" % testContainersVersion % It
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    dockerReleaseSettings
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
