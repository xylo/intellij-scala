package org.jetbrains

import _root_.java.io.File

import _root_.org.jetbrains.plugins.scala.DependencyManager
import _root_.org.jetbrains.plugins.scala.DependencyManagerBase._
import _root_.org.jetbrains.plugins.scala.buildinfo.BuildInfo
import _root_.org.jetbrains.plugins.scala.util.TestUtils
import _root_.org.jetbrains.sbt._
import _root_.org.jetbrains.sbt.settings.SbtSettings
import com.intellij.openapi.project.Project

/**
 * Utility to use with tests involving setup of sbt-launch or/and sbt-structure
 * @author Nikolay Obedin
 * @since 10/19/15.
 */
object SbtStructureSetup {

  val IvyCacheDir: File = new File(TestUtils.getIvyCachePath)

  def  setUpSbtLauncherAndStructure(project: Project): Unit = {
    val sbtVersion = Sbt.Latest_0_13 // hardcode latest version of sbt 0.13, still need to make test 1.0-capable
    val launcherVersion = Sbt.LatestVersion
    val sbtStructureVersion = BuildInfo.sbtStructureVersion
    val customSbtLauncher = DependencyManager.resolveSingle("org.scala-sbt"  % "sbt-launch" % launcherVersion.presentation).file
    val customSbtStructure = IvyCacheDir / "scala_2.10" / "sbt_0.13" / "org.jetbrains" / "sbt-structure-extractor" / "jars" / s"sbt-structure-extractor-$sbtStructureVersion.jar"

    assert(customSbtLauncher.isFile, s"sbt launcher not found at $customSbtLauncher")
    assert(customSbtStructure.isFile, s"sbt-structure not found at $customSbtStructure")

    val settings = SbtSettings.getInstance(project).getState
    settings.setCustomLauncherEnabled(true)
    settings.setCustomLauncherPath(customSbtLauncher.canonicalPath)
    settings.setCustomSbtStructurePath(customSbtStructure.canonicalPath)
    settings.vmParameters += s" -Dsbt.version=$sbtVersion"
    Option(System.getProperty("sbt.ivy.home")).foreach { ivyHome =>
      settings.vmParameters += s" -Dsbt.ivy.home=$ivyHome"
    }
  }
}
