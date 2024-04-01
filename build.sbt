ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "otus-graduation-work"
  )

// service
libraryDependencies += "dev.zio" %% "zio" % "2.0.10"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.10"
libraryDependencies += "dev.zio" %% "zio-kafka" % "2.3.1"
libraryDependencies += "dev.zio" %% "zio-json" % "0.5.0"
libraryDependencies += "dev.zio" %% "zio-config" % "4.0.0-RC16"
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "4.0.0-RC16"
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % "4.0.0-RC16"

// http
libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC3"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.4.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-zio" % "1.4.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio" % "1.4.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.9.11"
libraryDependencies += "org.http4s" %% "http4s-blaze-server" % "0.23.14"

// db
libraryDependencies += "io.getquill" %% "quill-zio" % "4.8.0"
libraryDependencies += "io.getquill" %% "quill-cassandra-zio" % "4.8.0"



