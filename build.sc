// build.sc
import mill._, scalalib._

trait JUnitTests extends TestModule {
  def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
  def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")
}

trait uTests extends TestModule {
  def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.6.3")
  def testFrameworks = Seq("utest.runner.Framework")
}

object graaljs extends JavaModule {

  lazy val graalvmVersion = "19.2.0.1"
  lazy val junitVersion = "4.12"

  override def ivyDeps = Agg(
    ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
    ivy"org.graalvm.js:js:$graalvmVersion",
    ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
    ivy"org.graalvm.tools:profiler:$graalvmVersion",
    ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
  )

  lazy val graalArgs = T {
    //val tmp = "/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler"
    val tmp = "/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler/compiler.jar:/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler/graal-sdk.jar:/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler/truffle-api.jar"
    Seq(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    s"--module-path=$tmp",
    s"--upgrade-module-path=/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler/compiler.jar"
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
