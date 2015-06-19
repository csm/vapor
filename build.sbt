name := "vapor"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

lazy val commonSettings = Seq(
  organization := "org.metastatic.vapor",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.6"
)

lazy val specs2core = "org.specs2" %% "specs2-core" % "2.4.14"

lazy val util = (project in file("vapor-util")).settings(commonSettings:_*)

lazy val core = (project dependsOn util in file("vapor-core")).settings(commonSettings:_*)

lazy val asgard = (project dependsOn(core, util) in file("vapor-asgard")).
  configs(IntegrationTest).
  settings(commonSettings:_*).
  settings(Defaults.itSettings:_*)

lazy val service = (project dependsOn(core, util, asgard) in file("vapor-service")).settings(commonSettings:_*)