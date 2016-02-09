name		:= "nuvo-core"

version		:= "0.3.0-SNAPSHOT"

organization 	:= "io.nuvo"

homepage :=  Some(new java.net.URL("http://nuvo.io"))

publishTo := Some(Resolver.file("file",  new File( "/Users/veda/hacking/zlab/mvn-repo/snapshots" )) )

scalaVersion 	:= "2.11.7"

autoCompilerPlugins := true

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-optimise"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

scalacOptions += "-Xlint"

//scalacOptions += "-Yinline-warnings"




