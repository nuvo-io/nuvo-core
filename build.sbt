name		:= "nuvo-core"

version		:= "0.1.1"

organization 	:= "io.nuvo"

homepage :=  Some(new java.net.URL("http://nuvo.io"))

scalaVersion 	:= "2.10.1"

seq(githubRepoSettings: _*)

localRepo := Path.userHome / "github" / "repo"

githubRepo := "git@github.com:nuvo-io/mvn-repo.git"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.1"

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.10.1"

libraryDependencies += "org.scala-lang" % "scala-actors" % "2.10.1"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"

autoCompilerPlugins := true

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-optimise"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

//scalacOptions += "-Yinline-warnings"




