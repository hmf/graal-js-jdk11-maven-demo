// build.sc
import mill._, scalalib._

trait JUnitTests extends TestModule {
  def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
  override def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")
}

trait uTests extends TestModule {
  override def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.7.1")
  def testFrameworks = Seq("utest.runner.Framework")
}

/**
 * mill mill.scalalib.GenIdea/idea
 *
 * mill -i graaljs.run
 * mill -i graaljs.test
 */
object graaljs extends ScalaModule {

  override def scalaVersion = "2.13.1"

  lazy val graalvmVersion = "19.2.0.1"
  lazy val junitVersion = "4.12"

  override def ivyDeps = Agg(
    ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
    ivy"org.graalvm.js:js:$graalvmVersion",
    ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
    ivy"org.graalvm.tools:profiler:$graalvmVersion",
    ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
  )

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

  lazy val graalArgs = T {
    val deps = graalToolsClasspath()
    val libPaths = deps.map(_.path.toIO.getAbsolutePath)
    val libPath = libPaths.mkString(java.io.File.pathSeparator)
    val compiler = libPaths.filter( _.matches(".+compiler.+\\.jar")).seq.toSeq.head
    Seq(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    s"--module-path=$libPath",
    s"--upgrade-module-path=$compiler"
    )
  }

  override def forkArgs = graalArgs

  object test extends Tests with uTests {
    override def forkArgs = graalArgs
  }

}
