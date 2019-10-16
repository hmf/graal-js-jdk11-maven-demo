// build.sc
import mill._
import mill.define.{Command, Input, Target}
import scalalib._

/**
 * https://github.com/oracle/graal/issues/651
 * https://medium.com/graalvm/graalvms-javascript-engine-on-jdk11-with-high-performance-3e79f968a819
 * https://github.com/graalvm/graal-js-jdk11-maven-demo
 *
 * mill mill.scalalib.GenIdea/idea
 *
 * mill graaljs.compile
 * mill graaljs.run
 * mill graaljs.run noGraal
 *
 * mill -i show graaljs.graalArgs
 * mill -i graaljs.run noGraal
 * mill -i graaljs.runMain com.mycompany.app.App noGraal
 * mill -i graaljs.runBackground noGraal
 * mill -i graaljs.runMainBackground com.mycompany.app.App noGraal
 *
 * mill -i graaljs.runLocal
 * mill -i graaljs.runMainLocal com.mycompany.app.App
 *
 * mill -i inspect graaljs.test.ivyDeps
 * mill -i show graaljs.test.ivyDeps
 * mill -i resolve graaljs.test._
 * mill -i show graaljs.test.upstreamAssemblyClasspath
 * mill -i show graaljs.test.runClasspath
 * mill -i show graaljs.test.compileClasspath
 * mill -i show graaljs.test.localClasspath
 * mill -i show graaljs.test.transitiveLocalClasspath
 *
 * mill graaljs.{compile, run}
 * mill --watch graaljs.run
 *
 * mill -i graaljs.console
 * mill -i graaljs.repl
 *
 * mill -i com.mycompany.app.App
 * mill --watch com.mycompany.app.App
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

  var useGraal = true

  /**
   * Sets the correct JVM parameters in order to enable or disable the GraalVM
   * modules, including the compiler. Note that this value cannot be cached
   * because it is always reset by the `run`, `runBackground`,
   * `runMainBackground` and `runMain` commands. We must either use a `T.task`
   * or `T.input`. We opted for a `T.input` because, unlike the task, it is
   * not cached in a cached target and is accessible as Mill command.
   *
   * @return
   */
  def graalArgs: Input[Seq[String]] = T.input {
    val deps = graalToolsClasspath()
    val libPaths = deps.map(_.path.toIO.getAbsolutePath)
    val libPath = libPaths.mkString(java.io.File.pathSeparator)
    if (useGraal) {
      val compiler = libPaths.filter( _.matches(".+compiler.+\\.jar")).seq.toSeq.head
      Seq(
      "-XX:+UnlockExperimentalVMOptions",
      "-XX:+EnableJVMCI",
      s"--module-path=$libPath",
      s"--upgrade-module-path=$compiler"
      )
    } else

    Seq(
      s"--module-path=$libPath"
    )
  }

  /**
   * Recalculate the arguments because the run and test commands may change
   * them.
   *
   * @return
   */
  override def forkArgs = super.forkArgs() ++ graalArgs()

  private val NOGRAAL = "nograal"

  /**
   * The GraavVM is used by default. It may be deactivated by passing the
   * "noGraal" parameter to the run and test commands. These fork arguments
   * are also used by the test module. However if we pass an invalid flag
   * to the test framework such as JUnit, it will fail silently. So we
   * remove the Graal flag so that the test can execute correctly.
   *
   * @param args command arguments to run and test
   */
  def setForkArgs(args: Seq[String]): Seq[String] = {
    useGraal = true
    val trimmedArgs = args.map(_.trim.toLowerCase)
    if ( trimmedArgs.contains(NOGRAAL) )
      useGraal = false
    trimmedArgs.filterNot(_.equals(NOGRAAL))
  }

  override def run(args: String*): Command[Unit] = T.command {

    super.run( setForkArgs(args):_* )
  }

  override def runBackground(args: String*): Command[Unit] = {

    super.runBackground( setForkArgs(args):_* )
  }

  override def runMainBackground(mainClass: String, args: String*): Command[Unit] = {
    super.runMainBackground(mainClass, setForkArgs(args):_*)
  }

  override def runMain(mainClass: String, args: String*): Command[Unit] = {
    super.runMain(mainClass, setForkArgs(args):_*)
  }

  /**
   * Tests fail.
   * @see https://github.com/lihaoyi/mill/issues/716
   */
  trait uTests extends TestModule {
    override def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.7.1")
    def testFrameworks: Target[Seq[String]] = Seq("utest.runner.Framework")

    override def forkArgs: Target[Seq[String]] = super.forkArgs() ++ graalArgs()

    override def test(oargs: String*): Command[(String, Seq[TestRunner.Result])] = T.command {
      val args = setForkArgs(oargs)
      super.test(args: _*)
    }
  }

  object test extends Tests with uTests

}
