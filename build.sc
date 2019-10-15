// build.sc
import mill._
import mill.api.Loose
import mill.define.Command
import os.{Path, RelPath}
import scalalib._

// https://github.com/lihaoyi/mill/blob/master/scalalib/src/MiscModule.scala

/**
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
 * mill graaljs.{compile, run}
 * mill --watch graaljs.run
 *
 * mill -i graaljs.console
 * mill -i graaljs.repl
 *
 * mill -i com.mycompany.app.App
 * mill --watch com.mycompany.app.App
 */
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
  def graalArgs = T.input {
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
  override def forkArgs = graalArgs()

  /**
   * The GraavVM is used by default. It may be deactivated by passing the
   * "noGraal" parameter to the run and test commands.
   *
   * @param args command arguments to run and test
   */
  def setForkArgs(args: Seq[String]): Unit = {
    useGraal = true
    if ( args.map(_.trim.toLowerCase).contains("nograal") )
      useGraal = false
  }

  override def run(args: String*): Command[Unit] = T.command {
    setForkArgs(args)
    super.run(args:_*)
  }

  override def runBackground(args: String*): Command[Unit] = {
    setForkArgs(args)
    super.runBackground(args:_*)
  }

  override def runMainBackground(mainClass: String, args: String*): Command[Unit] = {
    setForkArgs(args)
    super.runMainBackground(mainClass, args:_*)
  }

  override def runMain(mainClass: String, args: String*): Command[Unit] = {
    setForkArgs(args)
    super.runMain(mainClass, args:_*)
  }

  // TODO: no forkArgs
  override def runLocal(args: String*): Command[Unit] = T.command {
    useGraal = true
    if ( args.map(_.trim.toLowerCase).contains("nograal") )
      useGraal = false
    println(s"useGraal = $useGraal")
    super.runLocal(args:_*)
  }

  trait JUnitTests extends MavenTests {
    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
    override def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")

    // TODO override def test(args: String*): Command[(String, Seq[TestRunner.Result])] = super.test(args)
  }


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
