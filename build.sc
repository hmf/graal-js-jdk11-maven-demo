// build.sc
import mill._, scalalib._

trait JUnitTests extends TestModule {
  def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
  def ivyDeps = Agg(ivy"com.novocode:junit-interface:0.11")
}

object graaljs extends JavaModule {
  //def mainClass = Some("com.mycompany.app.App")

  lazy val graalvmVersion = "19.2.0"
  lazy val junitVersion = "4.12"

  override def ivyDeps = Agg(
    ivy"org.graalvm.sdk:graal-sdk:$graalvmVersion",
    ivy"org.graalvm.js:js:$graalvmVersion",
    ivy"org.graalvm.js:js-scriptengine:$graalvmVersion",
    ivy"org.graalvm.tools:profiler:$graalvmVersion",
    ivy"org.graalvm.tools:chromeinspector:$graalvmVersion",
    // Not usually required. Why do we need it?
    ivy"junit:junit:${junitVersion}"
  )

  object test extends Tests with JUnitTests {
    override def ivyDeps = super.ivyDeps
  }

}
