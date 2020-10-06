/*
 * Copyright (C) 2020 Edouard Fouché
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
name := "MiningTextOutliers"
organization:= "io.github.edouardfouche"

version := "0.1.0"

scalaVersion := "2.12.8"
crossScalaVersions := Seq("2.11.8", "2.12.8") // prefix with "+" to perform for both .e.g, "+ compile"

javaOptions += "-Xmx15G"
javaOptions += "-Xms10G"

fork in run := true
scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")

libraryDependencies += "de.lmu.ifi.dbs.elki" % "elki" % "0.7.5"

libraryDependencies += "de.lmu.ifi.dbs.elki" % "elki-logging" % "0.7.5"
libraryDependencies += "de.lmu.ifi.dbs.elki" % "elki-index-various" % "0.7.5"
libraryDependencies += "de.lmu.ifi.dbs.elki" % "elki-index-rtree" % "0.7.5"
libraryDependencies += "de.lmu.ifi.dbs.elki" % "elki-input" % "0.7.5"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies += "io.github.edouardfouche" %% "datagenerator" % "0.1.1"

resolvers += "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"

libraryDependencies += "org.jzy3d" % "jzy3d-api" % "1.0.0" //from "http://maven.jzy3d.org/releases/"
resolvers += "Jzy3d Maven Release Repository" at "http://maven.jzy3d.org/releases"

libraryDependencies  ++= Seq(
  // Last stable release
  "org.scalanlp" %% "breeze" % "1.0",

  // Native libraries are not included by default. add this if you want them
  // Native libraries greatly improve performance, but increase jar sizes.
  // It also packages various blas implementations, which have licenses that may or may not
  // be compatible with the Apache License. No GPL code, as best I know.
  "org.scalanlp" %% "breeze-natives" % "1.0",

  // The visualization library is distributed separately as well.
  // It depends on LGPL code
  "org.scalanlp" %% "breeze-viz" % "1.0"
)

resolvers ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/releases/"
)

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

javacOptions ++= Seq("-encoding", "UTF-8")