// build.sc
import mill._, scalalib._

trait JUnitTests extends TestModule {
  def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
  def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")
}

object jarpath extends ScalaModule {

  override def scalaVersion = "2.13.1"

  override def ivyDeps = Agg(
    ivy"io.get-coursier::coursier:2.0.0-RC3-4"
  )
}

/*
  Notes:
    - To retain the original set-up we need to override Mill's defaults
        . def millSourcePath = super.millSourcePath / os.up.
    See example: https://github.com/lihaoyi/mill/blob/master/integration/test/resources/caffeine/build.sc
    See https://github.com/lihaoyi/mill/blob/master/scalalib/src/MiscModule.scala
 */
object graaljs extends JavaModule {
  //def mainClass = Some("com.mycompany.app.App")

  lazy val graalvmVersion = "19.2.0.1"
  lazy val junitVersion = "4.12"

  override def ivyDeps = Agg(
    ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
    ivy"org.graalvm.js:js:$graalvmVersion",
    ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
    ivy"org.graalvm.tools:profiler:$graalvmVersion",
    ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
  )

  // https://github.com/lefou/mill-aspectj/blob/master/aspectj/src/de/tobiasroeser/mill/aspectj/AspectjModule.scala#L47
  def graalToolsDeps: T[Agg[Dep]] = T {
    Agg(
      ivy"org.graalvm.compiler:compiler:${graalvmVersion}",    // compiler.jar
      ivy"org.graalvm.truffle:truffle-api:${graalvmVersion}",  // truffle-api.jar
      ivy"org.graalvm.sdk:graal-sdk:${graalvmVersion}"         // graal-sdk.jar
    )
  }

  def graalToolsClasspath: T[Agg[PathRef]] = T {
    resolveDeps(graalToolsDeps)
  }


  def getCompilerPath(paths:Seq[java.io.File]): String = {
    val compilerPath = paths.find{ f =>
      f.getAbsolutePath.matches(".+compiler.+\\.jar")
    }

    compilerPath match {
      case Some(path) =>
        if (path.isFile) {
          println("Is a file ------------------- ")
          println(s"path.getParent = ${path.getParent}")
          println(s"path.getAbsolutePath = ${path.getAbsolutePath}")
          path.getAbsolutePath
        }
        else
          throw new RuntimeException("Graal compiler path incorrect.")

      case None =>
        throw new RuntimeException("Graal compiler not found.")
    }
  }

  def fetch(): String = {
    import coursier._
    import coursier.cache._

    val files = Fetch()
      .addDependencies(dep"org.graalvm.compiler:compiler:19.2.0.1")
      .run()
    val path = getCompilerPath(files)
    path
  }

  lazy val compilerDir = fetch()


  lazy val graalArgs = Seq(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    // TODO: s"--module-path=${compilerDir}",
    //s"--upgrade-module-path=${compilerDir}/compiler.jar"
    s"--upgrade-module-path=$compilerDir"
  )


  override def forkArgs = graalArgs

  //def forkEnv = Map("HELLO_MY_ENV_VAR" -> "WORLD")

  object test extends Tests with JUnitTests {
    // Seems we don't need JUnit deps
    override def ivyDeps = super.ivyDeps() ++ Agg(ivy"junit:junit:${junitVersion}")
    override def forkArgs = graalArgs
  }

}
