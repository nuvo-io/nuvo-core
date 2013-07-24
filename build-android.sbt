name		:= "nuvo-core-android"

version		:= "0.1.2-SNAPSHOT"

organization 	:= "io.nuvo"

homepage :=  Some(new java.net.URL("http://nuvo.io"))

scalaVersion 	:= "2.10.2"

seq(githubRepoSettings: _*)

localRepo := Path.userHome / "github" / "repo"

githubRepo := "git@github.com:nuvo-io/mvn-repo.git"


autoCompilerPlugins := true

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-optimise"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

scalacOptions += "-Xlint"




