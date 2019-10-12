// build.sc
import mill._
import os.{Path, RelPath}
import scalalib._

trait JUnitTests extends MavenTests {
  def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
  override def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")
}

// https://github.com/lihaoyi/mill/blob/master/scalalib/src/MiscModule.scala

object graaljs extends MavenModule {

  lazy val graalvmVersion = "19.2.0.1"
  lazy val junitVersion = "4.12"

  //override def millSourcePath = millModuleBasePath.value  // stack overflow
  override def millSourcePath = super.millSourcePath / os.up

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

  /*
  object test extends Tests with uTests {

    // Seems we don't need JUnit deps
    override def ivyDeps = super.ivyDeps() ++
      Agg(
        ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
        ivy"org.graalvm.js:js:$graalvmVersion",
        ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
        ivy"org.graalvm.tools:profiler:$graalvmVersion",
        ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
      )
    override def forkArgs = graalArgs
  }*/

  // mill -i inspect graaljs.test.ivyDeps
  // mill -i show graaljs.test.ivyDeps
  // mill -i resolve graaljs.test._
  // mill -i show graaljs.test.upstreamAssemblyClasspath
  // mill -i show graaljs.test.runClasspath
  // mill -i show graaljs.test.compileClasspath
  // mill -i show graaljs.test.localClasspath
  // mill -i show graaljs.test.transitiveLocalClasspath
  object test extends Tests with JUnitTests {

    /*
    // Seems we don't need JUnit deps
    override def ivyDeps = super.ivyDeps() ++ Agg(ivy"junit:junit:${junitVersion}") ++
      Agg(
        ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
        ivy"org.graalvm.js:js:$graalvmVersion",
        ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
        ivy"org.graalvm.tools:profiler:$graalvmVersion",
        ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
      )
     */
    override def forkArgs = graalArgs
  }
  /*
  object test extends Tests {
    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")

    // Seems we don't need JUnit deps
    override def ivyDeps =
      Agg(
        ivy"com.novocode:junit-interface:0.11",
        ivy"junit:junit:${junitVersion}",
        ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
        ivy"org.graalvm.js:js:$graalvmVersion",
        ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
        ivy"org.graalvm.tools:profiler:$graalvmVersion",
        ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
      )
    override def forkArgs = graalArgs
  }
   */

}
