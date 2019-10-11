// build.sc
import mill._, scalalib._

trait JUnitTests extends TestModule {
  def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
  def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")
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
    val tmp = "/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler"
    Seq(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    s"--module-path=$tmp",
    s"--upgrade-module-path=/home/hmf/IdeaProjects/graal-js-jdk11-maven-demo/target/compiler/compiler.jar"
    )
  }

  override def forkArgs = graalArgs

  object test extends Tests with JUnitTests {

    // Seems we don't need JUnit deps
    override def ivyDeps = super.ivyDeps() ++ Agg(ivy"junit:junit:${junitVersion}") ++
      Agg(
        ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
        ivy"org.graalvm.js:js:$graalvmVersion",
        ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
        ivy"org.graalvm.tools:profiler:$graalvmVersion",
        ivy"org.graalvm.tools:chromeinspector:$graalvmVersion"
      )
    override def forkArgs = graalArgs
  }

}
