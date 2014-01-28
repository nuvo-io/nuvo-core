name		:= "nuvo-core"

version		:= "0.2.1-SNAPSHOT"

organization 	:= "io.nuvo"

homepage :=  Some(new java.net.URL("http://nuvo.io"))

publishTo := Some(Resolver.file("file",  new File( "/Users/nuvo/Labs/mvn-repo/snapshots" )) )

scalaVersion 	:= "2.10.3"

autoCompilerPlugins := true

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-optimise"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

scalacOptions += "-Xlint"

//scalacOptions += "-Yinline-warnings"




