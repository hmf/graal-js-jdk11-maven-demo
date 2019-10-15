// build.sc
import mill._
import mill.api.Loose
import mill.define.{Command, Target}
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

  // TODO: no forkArgs
  override def runLocal(args: String*): Command[Unit] = T.command {
    useGraal = true
    if ( args.map(_.trim.toLowerCase).contains("nograal") )
      useGraal = false
    println(s"useGraal = $useGraal")
    super.runLocal(args:_*)
  }

  trait JUnitTests extends MavenTests {
    import mill.eval.Result
    import mill.modules.Jvm

    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
    override def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")

    override def forkArgs: Target[Seq[String]] = super.forkArgs() ++ graalArgs()

    // TODO override def test(args: String*): Command[(String, Seq[TestRunner.Result])] = super.test(args)
    // TODO: testMain
    override def test(oargs: String*) = T.command {
      val outputPath = T.ctx().dest/"out.json"

      // TODO: add in override
      val args = setForkArgs(oargs)

      Jvm.runSubprocess(
        mainClass = "mill.scalalib.TestRunner",
        //classPath = zincWorker.scalalibClasspath().map(_.path),
        classPath = runClasspath().map(_.path) ++ zincWorker.scalalibClasspath().map(_.path), // requires mill.scalalib.TestRunner
        jvmArgs = forkArgs(),
        envArgs = forkEnv(),
        mainArgs =
          Seq(testFrameworks().length.toString) ++
            testFrameworks() ++
            Seq(runClasspath().length.toString) ++
            runClasspath().map(_.path.toString) ++
            Seq(args.length.toString) ++
            args ++
            Seq(outputPath.toString, T.ctx().log.colored.toString, compile().classes.path.toString, T.ctx().home.toString),
        workingDir = forkWorkingDir()
      )

      try {
        val jsonOutput = ujson.read(outputPath.toIO)
        val (doneMsg, results) = upickle.default.read[(String, Seq[TestRunner.Result])](jsonOutput)
        TestModule.handleResults(doneMsg, results)
      }catch{case e: Throwable =>
        Result.Failure("Test reporting failed: " + e)
      }

    }

/*
https://github.com/lihaoyi/mill/blob/9ba4cb69331386dfde9bac69dc2d5b22401face3/scalalib/src/TestRunner.scala#L14
java -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI --module-path=/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/compiler/compiler/19.2.0.1/compiler-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/truffle/truffle-api/19.2.0.1/truffle-api-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/sdk/graal-sdk/19.2.0.1/graal-sdk-19.2.0.1.jar --upgrade-module-path=/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/compiler/compiler/19.2.0.1/compiler-19.2.0.1.jar -classpath /home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/src/test/resources:/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/out/graaljs/test/compile/dest/classes:/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/src/main/resources:/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/out/graaljs/compile/dest/classes:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/regex/regex/19.2.0.1/regex-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/js/js/19.2.0.1/js-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/novocode/junit-interface/0.11/junit-interface-0.11.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/ow2/asm/asm-commons/6.2.1/asm-commons-6.2.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-sbt/test-interface/1.0/test-interface-1.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/truffle/truffle-api/19.2.0.1/truffle-api-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/ow2/asm/asm-analysis/6.2.1/asm-analysis-6.2.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/ow2/asm/asm-tree/6.2.1/asm-tree-6.2.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/js/js-scriptengine/19.2.0.1/js-scriptengine-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/tools/chromeinspector/19.2.0.1/chromeinspector-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/ow2/asm/asm/6.2.1/asm-6.2.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/tools/profiler/19.2.0.1/profiler-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/ibm/icu/icu4j/62.1/icu4j-62.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/graalvm/sdk/graal-sdk/19.2.0.1/graal-sdk-19.2.0.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/junit/junit/4.11/junit-4.11.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/ow2/asm/asm-util/6.2.1/asm-util-6.2.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/jline/jline-terminal/3.6.2/jline-terminal-3.6.2.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-main_2.12/0.5.1/mill-main_2.12-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upack_2.12/0.7.5/upack_2.12-0.7.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/scalaparse_2.12/2.1.3/scalaparse_2.12-2.1.3.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.12/1.2.0/scala-xml_2.12-1.2.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-interpApi_2.12.8/1.6.9/ammonite-interpApi_2.12.8-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.12/0.1.7/sourcecode_2.12-0.1.7.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.12/0.3.0/os-lib_2.12-0.3.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scalameta/scalafmt-interfaces/2.0.0-RC6/scalafmt-interfaces-2.0.0-RC6.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite_2.12.8/1.6.9/ammonite_2.12.8-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/io/argonaut/argonaut_2.12/6.2.3/argonaut_2.12-6.2.3.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/jline/jline-reader/3.6.2/jline-reader-3.6.2.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/net/java/dev/jna/jna-platform/4.5.0/jna-platform-4.5.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-main-core_2.12/0.5.1/mill-main-core_2.12-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.12/2.0.0/scala-collection-compat_2.12-2.0.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.12/0.7.5/upickle_2.12-0.7.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/geirsson/coursier-small_2.12/1.3.1/coursier-small_2.12-1.3.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/requests_2.12/0.2.0/requests_2.12-0.2.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.8/scala-reflect-2.12.8.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.12.8/scala-compiler-2.12.8.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/io/get-coursier/coursier_2.12/2.0.0-RC2/coursier_2.12-2.0.0-RC2.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/pprint_2.12/0.5.5/pprint_2.12-0.5.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-main-api_2.12/0.5.1/mill-main-api_2.12-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-repl_2.12.8/1.6.9/ammonite-repl_2.12.8-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-sbt/ipcsocket/ipcsocket/1.0.0/ipcsocket-1.0.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/scopt/scopt_2.12/3.7.1/scopt_2.12-3.7.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/geny_2.12/0.1.8/geny_2.12-0.1.8.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.12/0.7.5/upickle-implicits_2.12-0.7.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-terminal_2.12/1.6.9/ammonite-terminal_2.12-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-scalalib-api_2.12/0.5.1/mill-scalalib-api_2.12-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-runtime_2.12/1.6.9/ammonite-runtime_2.12-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/fansi_2.12/0.2.7/fansi_2.12-0.2.7.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-main-client/0.5.1/mill-main-client-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/io/get-coursier/coursier-core_2.12/2.0.0-RC2/coursier-core_2.12-2.0.0-RC2.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-interp_2.12.8/1.6.9/ammonite-interp_2.12.8-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/typesafe/config/1.3.3/config-1.3.3.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/net/java/dev/jna/jna/4.5.0/jna-4.5.0.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-scalalib_2.12/0.5.1/mill-scalalib_2.12-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scalameta/scalafmt-dynamic_2.12/2.0.0-RC6/scalafmt-dynamic_2.12-2.0.0-RC6.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-util_2.12/1.6.9/ammonite-util_2.12-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.12/2.0.0-RC2/coursier-cache_2.12-2.0.0-RC2.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.12/0.7.5/ujson_2.12-0.7.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.12/0.7.5/upickle-core_2.12-0.7.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-ops_2.12/1.6.9/ammonite-ops_2.12-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ammonite-replApi_2.12.8/1.6.9/ammonite-replApi_2.12.8-1.6.9.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/mill-main-moduledefs_2.12/0.5.1/mill-main-moduledefs_2.12-0.5.1.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/fastparse_2.12/2.1.3/fastparse_2.12-2.1.3.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/alexarchambault/argonaut-shapeless_6.2_2.12/1.2.0-M11/argonaut-shapeless_6.2_2.12-1.2.0-M11.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.2.5/javaparser-core-3.2.5.jar:/home/hmf/.cache/coursier/v1/https/repo1.maven.org/maven2/org/jline/jline-terminal-jna/3.6.2/jline-terminal-jna-3.6.2.jar mill.scalalib.TestRunner
 */

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
    //override def forkArgs = graalArgs
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
