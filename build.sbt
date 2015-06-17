name := "vapor"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

lazy val commonSettings = Seq(
  organization := "org.metastatic.vapor",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.6"
)

lazy val core = (project in file("vapor-core")).settings(commonSettings:_*)

lazy val aws = (project dependsOn core in file("vapor-aws")).settings(commonSettings:_*)