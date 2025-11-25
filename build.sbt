ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version      := "0.1.0"

val chiselVersion   = "7.3.0"
val mainargsVersion = "0.7.6"

lazy val resampler = project
  .in(file("."))
  .settings(
    name := "sinc-rsmp",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel"    % chiselVersion,
      "org.scalatest"     %% "scalatest" % "3.2.19" % Test,
      "com.lihaoyi"       %% "mainargs"  % mainargsVersion
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Ymacro-annotations"
    ),
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    )
  )
