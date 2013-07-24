name		:= "nuvo-core"

version		:= "0.1.2"

organization 	:= "io.nuvo"

homepage :=  Some(new java.net.URL("http://nuvo.io"))

scalaVersion 	:= "2.10.2"

seq(githubRepoSettings: _*)

localRepo := Path.userHome / "github" / "repo"

githubRepo := "git@github.com:nuvo-io/mvn-repo.git"


autoCompilerPlugins := true

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-optimise"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

scalacOptions += "-Xlint"

//scalacOptions += "-Yinline-warnings"




