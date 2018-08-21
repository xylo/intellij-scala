package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightingLevelManager}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import org.jetbrains.plugins.scala.actions.decompileToJava.DecompiledScalaFileWritingAccessProvider
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.highlighter.AnnotatorHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

class ScalaAnnotatorHighlightVisitor(project: Project) extends HighlightVisitor {
  implicit def ctx: ProjectContext = project

  override def order: Int = 0

  private var myHolder: HighlightInfoHolder = _
  private var myRefCountHolder: ScalaRefCountHolder = _
  private var myAnnotationHolder: AnnotationHolderImpl = _

  override def suitableForFile(file: PsiFile): Boolean = file match {
    case _: ScalaFile => true
    case otherFile => ScalaLanguageDerivative hasDerivativeOnFile otherFile
  }

  def visit(element: PsiElement) {
    runAnnotator(element)
  }

  def analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean = {
//    val time = System.currentTimeMillis()
    var success = true
    try {
      myHolder = holder
      myAnnotationHolder = new AnnotationHolderImpl(holder.getAnnotationSession)
      if (updateWholeFile) {
        val refCountHolder: ScalaRefCountHolder = ScalaRefCountHolder.getInstance(file)
        myRefCountHolder = refCountHolder
        val dirtyScope = ScalaRefCountHolder.getDirtyScope(file).orNull
        success = refCountHolder.analyze(action, dirtyScope, file)
      } else {
        myRefCountHolder = null
        action.run()
      }
    } finally {
      myHolder = null
      myAnnotationHolder = null
      myRefCountHolder = null
    }
    // TODO We should probably create a dedicated registry property that enables printing of the running time.
    // Otherwise, the output always pollutes the console, even when there's no need for that data.
    // IDEA's "internal mode" is a too coarse-grained switch for that.
//    val method: Long = System.currentTimeMillis() - time
//    if (method > 100 && ApplicationManager.getApplication.isInternal) println(s"File: ${file.getName}, Time: $method")
    success
  }

  override def clone: HighlightVisitor = {
    new ScalaAnnotatorHighlightVisitor(project)
  }

  private def runAnnotator(element: PsiElement) {
    if (DumbService.getInstance(project).isDumb) {
      return
    }
    val file = element.getContainingFile
    if (file == null || 
      DecompiledScalaFileWritingAccessProvider.isDecompiledFile(file.getVirtualFile)) return

    if (shouldHighlight(file)) {
      AnnotatorHighlighter.highlightElement(element, myAnnotationHolder)
    }

    if (shouldInspect(file)) {
      ScalaAnnotator.forProject.annotate(element, myAnnotationHolder)
    }

    myAnnotationHolder.forEach { annotation =>
      myHolder.add(HighlightInfo.fromAnnotation(annotation))
    }
    myAnnotationHolder.clear()
  }

  private def shouldInspect(file: PsiFile) =
    file != null && HighlightingLevelManager.getInstance(project).shouldInspect(file) ||
      ApplicationManager.getApplication.isUnitTestMode

  private def shouldHighlight(file: PsiFile) =
    file != null && HighlightingLevelManager.getInstance(project).shouldHighlight(file)
}