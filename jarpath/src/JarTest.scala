import coursier._
import coursier.cache._

// coursier.cache.Cache.default.fetch
// https://github.com/lihaoyi/mill/blob/master/contrib/bloop/src/mill.contrib.bloop/BloopImpl.scala

object JarTest {

  def getCompilerPath(paths:Seq[java.io.File]) = {
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

  def main(args: Array[String]): Unit = {
      println("Hello world")

    val cache = FileCache()
    println(cache)
    val resolution = Resolve()
      .addDependencies(dep"org.graalvm.compiler:compiler:19.2.0.1")
      .run()
    println(resolution)

    val graalvmVersion = "19.2.0.1"

    val files = Fetch()
      .addDependencies(dep"org.graalvm.compiler:compiler:19.2.0.1")
      .run()
    println(files.mkString(";\n"))
    val path = getCompilerPath(files)
    println(path)
  }
}