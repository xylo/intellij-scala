package org.jetbrains.plugins.scala
package debugger

import java.util
import java.util.Collections

import com.intellij.debugger.engine.{CompoundPositionManager, DebugProcess, DebugProcessImpl}
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.{MultiRequestPositionManager, NoDataException, PositionManager, SourcePosition}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import com.intellij.util.{Processor, Query}
import com.sun.jdi._
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager._
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaCompilingEvaluator
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import org.jetbrains.plugins.scala.util.macroDebug.ScalaMacroDebuggingUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * @author ilyas
 */
class ScalaPositionManager(debugProcess: DebugProcess) extends PositionManager with MultiRequestPositionManager {
  def getDebugProcess = debugProcess

  @Nullable
  def getSourcePosition(@Nullable location: Location): SourcePosition = {
    def calcLineIndex(location: Location): Int = {
      LOG.assertTrue(getDebugProcess != null)
      try {
        customLineNumber(location).getOrElse(location.lineNumber - 1)
      }
      catch {
        case e: InternalError => -1
      }
    }

    val position =
      for {
        loc <- location.toOption
        psiFile <- getPsiFileByLocation(getDebugProcess.getProject, loc).toOption
        lineNumber = calcLineIndex(loc)
        if lineNumber >= 0
      } yield {
        calcPosition(psiFile, location, lineNumber).getOrElse {
          SourcePosition.createFromLine(psiFile, lineNumber)
        }
      }
    position match {
      case Some(p) => p
      case None => throw NoDataException.INSTANCE
    }
  }

  @NotNull
  def getAllClasses(@NotNull position: SourcePosition): util.List[ReferenceType] = {

    checkScalaFile(position)
    val file = position.getFile

    def hasLocations(refType: ReferenceType, position: SourcePosition): Boolean = {
      try {
        if (!file.isPhysical) { //may be generated in compiling evaluator
        val generatedClassName = file.getUserData(ScalaCompilingEvaluator.classNameKey)
          generatedClassName != null && refType.name().contains(generatedClassName)
        }
        else locationsOfLine(refType, position).size > 0
      } catch {
        case _: NoDataException | _: AbsentInformationException | _: ClassNotPreparedException | _: ObjectCollectedException => false
      }
    }

    val possiblePositions = positionsOnLine(file, position.getLine)
    val exactClasses = ArrayBuffer[ReferenceType]()
    val namePatterns = ArrayBuffer[NamePattern]()
    inReadAction {
      val sourceImages = possiblePositions.map(findGeneratingClassParent)
      sourceImages.foreach {
        case null =>
        case td: ScTypeDefinition if !DebuggerUtil.isLocalClass(td) =>
          val qName = getSpecificNameForDebugger(td)
          if (qName != null)
            exactClasses ++= getDebugProcess.getVirtualMachineProxy.classesByName(qName).asScala
        case elem =>
          val namePattern = NamePattern.forElement(elem)
          namePatterns ++= Option(namePattern)
      }
    }
    val foundWithPattern = filterAllClasses(c => namePatterns.exists(_.matches(c)) && hasLocations(c, position))
    (exactClasses ++ foundWithPattern).distinct.asJava
  }

  @NotNull
  def locationsOfLine(@NotNull refType: ReferenceType, @NotNull position: SourcePosition): util.List[Location] = {
    def findCustomizedLocations(line: Int) = {
      val allLocations = refType.allLineLocations().asScala
      allLocations.filter { l =>
        val custom = customLineNumber(l)
        custom.isDefined && custom.contains(line - 1)
      }.asJava
    }

    checkScalaFile(position)

    try {
      val line: Int = position.getLine + 1
      val jvmLocations: util.List[Location] =
        if (getDebugProcess.getVirtualMachineProxy.versionHigher("1.4"))
          refType.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line)
        else refType.locationsOfLine(line)
      val nonCustomizedJvm = jvmLocations.asScala.filter(l => customLineNumber(l).isEmpty).asJava
      val customized = findCustomizedLocations(line)
      val size = nonCustomizedJvm.size + customized.size

      val all = new util.ArrayList[Location](size)
      all.addAll(nonCustomizedJvm)
      all.addAll(customized)
      all
    }
    catch {
      case e: AbsentInformationException => Collections.emptyList()
    }
  }

  def createPrepareRequest(@NotNull requestor: ClassPrepareRequestor, @NotNull position: SourcePosition): ClassPrepareRequest = {
    throw new IllegalStateException("This class implements MultiRequestPositionManager, corresponding createPrepareRequests version should be used")
  }

  override def createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): util.List[ClassPrepareRequest] = {
    def isLocalOrUnderDelayedInit(definition: PsiClass): Boolean = {
      def isDelayed = definition match {
        case obj: ScObject =>
          val manager: ScalaPsiManager = ScalaPsiManager.instance(obj.getProject)
          val clazz: PsiClass = manager.getCachedClass(obj.getResolveScope, "scala.DelayedInit")
          clazz != null && manager.cachedDeepIsInheritor(obj, clazz)
        case _ => false
      }
      DebuggerUtil.isLocalClass(definition) || isDelayed
    }

    def findEnclosingTypeDefinition: Option[ScTypeDefinition] = {
      @tailrec
      def notLocalEnclosingTypeDefinition(element: PsiElement): Option[ScTypeDefinition] = {
        PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition]) match {
          case null => None
          case td if DebuggerUtil.isLocalClass(td) => notLocalEnclosingTypeDefinition(td.getParent)
          case td => Some(td)
        }
      }
      val element = nonWhitespaceElement(position)
      notLocalEnclosingTypeDefinition(element)
    }

    def createPrepareRequest(position: SourcePosition): ClassPrepareRequest = {
      val qName = new Ref[String](null)
      val waitRequestor = new Ref[ClassPrepareRequestor](null)
      inReadAction {
        val sourceImage = findReferenceTypeSourceImage(position)
        val insideMacro: Boolean = isInsideMacro(nonWhitespaceElement(position))
        sourceImage match {
          case cl: ScClass if ValueClassType.isValueClass(cl) =>
            //there are no instances of value classes, methods from companion object are used
            qName.set(getSpecificNameForDebugger(cl) + "$")
          case typeDef: ScTypeDefinition if !isLocalOrUnderDelayedInit(typeDef) =>
            val specificName = getSpecificNameForDebugger(typeDef)
            qName.set(if (insideMacro) specificName + "*" else specificName)
          case _ =>
            findEnclosingTypeDefinition.foreach(typeDef => qName.set(typeDef.getQualifiedNameForDebugger + "*"))
        }
        // Enclosing type definition is not found
        if (qName.get == null) {
          qName.set(SCRIPT_HOLDER_CLASS_NAME + "*")
        }
        waitRequestor.set(new ScalaPositionManager.MyClassPrepareRequestor(position, requestor))
      }

      getDebugProcess.getRequestsManager.createClassPrepareRequest(waitRequestor.get, qName.get)
    }

    checkScalaFile(position)

    val possiblePositions = inReadAction {
      positionsOnLine(position.getFile, position.getLine).map(SourcePosition.createFromElement)
    }
    possiblePositions.map(createPrepareRequest).asJava
  }

  private def checkScalaFile(@NotNull position: SourcePosition): Unit = {
    position.getFile match {
      case _: ScalaFile =>
      case _ => throw NoDataException.INSTANCE
    }
  }

  private def filterAllClasses(condition: ReferenceType => Boolean): Seq[ReferenceType] = {
    import scala.collection.JavaConverters._
    getDebugProcess.getVirtualMachineProxy.allClasses.asScala.filter(condition)
  }

  @tailrec
  private def findGeneratingClassParent(element: PsiElement): PsiElement = {
    element match {
      case null => null
      case elem if ScalaEvaluatorBuilderUtil.isGenerateClass(elem) => elem
      case expr: ScExpression if isInsideMacro(element) => expr
      case elem => findGeneratingClassParent(elem.getParent)
    }
  }

  @Nullable
  private def findReferenceTypeSourceImage(@NotNull position: SourcePosition): PsiElement = {
    val element = nonWhitespaceElement(position)
    findGeneratingClassParent(element)
  }

  private def nonWhitespaceElement(@NotNull position: SourcePosition): PsiElement = {
    val file = position.getFile
    @tailrec
    def nonWhitespaceInner(element: PsiElement, document: Document): PsiElement = {
      element match {
        case null => null
        case ws: PsiWhiteSpace if document.getLineNumber(element.getTextRange.getEndOffset) == position.getLine =>
          val nextElement = file.findElementAt(element.getTextRange.getEndOffset)
          nonWhitespaceInner(nextElement, document)
        case _ => element
      }
    }
    if (!file.isInstanceOf[ScalaFile]) null
    else {
      val firstElement = file.findElementAt(position.getOffset)
      try {
        val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
        nonWhitespaceInner(firstElement, document)
      }
      catch {
        case t: Throwable => firstElement
      }
    }
  }

  private def isInsideMacro(element: PsiElement): Boolean = {
    var call = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall])
    while (call != null) {
      call.getEffectiveInvokedExpr match {
        case resRef: ResolvableReferenceElement =>
          if (resRef.resolve().isInstanceOf[ScMacroDefinition]) return true
        case _ =>
      }
      call = PsiTreeUtil.getParentOfType(call, classOf[ScMethodCall])
    }
    false
  }

  private def customLineNumber(location: Location): Option[Int] = {
    //scalac sometimes generates very strange line numbers for <init> method
    def lineForConstructor: Option[Int] = {
      val declType = location.declaringType()
      inReadAction {
        findPsiClassByReferenceType(declType) match {
          case Some(c) =>
            val doc = PsiDocumentManager.getInstance(getDebugProcess.getProject).getDocument(c.getContainingFile)
            Some(doc.getLineNumber(c.getTextOffset))
          case None => None
        }
      }
    }

    if (location.method.isConstructor && location.method.location == location) lineForConstructor
    else None
  }

  private def calcPosition(file: PsiFile, location: Location, lineNumber: Int): Option[SourcePosition] = {
    def trivialChoice(elems: Seq[PsiElement]): Option[SourcePosition] = {
      elems match {
        case Seq() => None
        case Seq(elem) => Some(SourcePosition.createFromElement(elem))
      }
    }

    def ordinal(refType: ReferenceType): Option[Int] = {
      val name = refType.name()
      Try(name.substring(name.lastIndexOf('$') + 1).toInt).toOption
    }

    val scFile = file match {
      case sf: ScalaFile if !sf.isCompiled => sf
      case _ => return None
    }

    val possiblePositions = positionsOnLine(scFile, lineNumber)
    if (possiblePositions.size <= 1) return trivialChoice(possiblePositions)

    val methodName = location.method().name()

    def findDefaultArg: Option[SourcePosition] = {
      try {
        val (start, index) = methodName.splitAt(methodName.lastIndexOf("$") + 1)
        if (!start.endsWith("$default$")) return None

        val paramNumber = index.toInt - 1
        val inDefaultParam = possiblePositions.find {
          case e =>
            val scParameters = PsiTreeUtil.getParentOfType(e, classOf[ScParameters])
            if (scParameters != null) {
              val param = scParameters.params(paramNumber)
              param.isDefaultParam && param.isAncestorOf(e)
            }
            else false
        }
        inDefaultParam.map(SourcePosition.createFromElement)
      } catch {
        case e: Exception => None
      }
    }

    if (methodName.contains("$default$")) {
      return findDefaultArg
    }

    val declaringType = location.declaringType()
    val withMatchingName = possiblePositions.filter(elem => nameMatches(findGeneratingClassParent(elem), declaringType))
    if (withMatchingName.size <= 1) return trivialChoice(withMatchingName)

    val example = withMatchingName.head
    val similarRefTypes = filterAllClasses(c => nameMatches(example, c) && !c.locationsOfLine(lineNumber + 1).isEmpty)
    val sorted = similarRefTypes.sortBy(ordinal)
    val index = sorted.indexOf(declaringType)
    withMatchingName.lift(index).map(SourcePosition.createFromElement)
  }

  private def findScriptFile(location: Location): Option[PsiFile] = {
    try {
      val refType = location.declaringType()
      val name = refType.name()
      if (name.startsWith(SCRIPT_HOLDER_CLASS_NAME)) {
        val sourceName = location.sourceName
        val files = FilenameIndex.getFilesByName(getDebugProcess.getProject, sourceName, getDebugProcess.getSearchScope)
        files.headOption
      }
      else None
    }
    catch {
      case e: AbsentInformationException => None
    }
  }

  private def findClassByQualName(qName: String): Option[PsiClass] = {
    val project = getDebugProcess.getProject
    val cacheManager = ScalaShortNamesCacheManager.getInstance(project)
    val packageSuffix = ".package$"
    val classes =
      if (qName.endsWith(packageSuffix))
        Seq(cacheManager.getPackageObjectByName(qName.stripSuffix(packageSuffix), GlobalSearchScope.allScope(project)))
      else
        cacheManager.getClassesByFQName(qName, getDebugProcess.getSearchScope)

    val clazz =
      if (classes.length == 1) classes.headOption
      else if (classes.length == 2 && ScalaPsiUtil.getCompanionModule(classes.head).contains(classes(1))) classes.headOption
      else None
    clazz.filter(_.isValid)
  }

  @Nullable
  private def getPsiFileByLocation(project: Project, location: Location): PsiFile = {

    def qualName(refType: ReferenceType): String = {
      val originalQName = refType.name.replace('/', '.')
      if (originalQName.endsWith("package$")) return originalQName

      val dollar: Int = originalQName.indexOf('$')
      if (dollar >= 0) originalQName.substring(0, dollar) else originalQName
    }

    def searchForMacroDebugging(qName: String): PsiFile = {
      val directoryIndex: DirectoryIndex = DirectoryIndex.getInstance(project)
      val dotIndex = qName.lastIndexOf(".")
      val packageName = if (dotIndex > 0) qName.substring(0, dotIndex) else ""
      val query: Query[VirtualFile] = directoryIndex.getDirectoriesByPackageName(packageName, true)
      val fileNameWithoutExtension = if (dotIndex > 0) qName.substring(dotIndex + 1) else qName
      val fileNames: util.Set[String] = new util.HashSet[String]
      import scala.collection.JavaConversions._
      for (extention <- ScalaLoader.SCALA_EXTENSIONS) {
        fileNames.add(fileNameWithoutExtension + "." + extention)
      }
      val result = new Ref[PsiFile]
      query.forEach(new Processor[VirtualFile] {
        override def process(vDir: VirtualFile): Boolean = {
          var isFound = false
          for {
            fileName <- fileNames
            if !isFound
            vFile <- vDir.findChild(fileName).toOption
          } {
            val psiFile: PsiFile = PsiManager.getInstance(project).findFile(vFile)
            val debugFile: PsiFile = ScalaMacroDebuggingUtil.loadCode(psiFile, force = false)
            if (debugFile != null) {
              result.set(debugFile)
              isFound = true
            }
            else if (psiFile.isInstanceOf[ScalaFile]) {
              result.set(psiFile)
              isFound = true
            }
          }
          !isFound
        }
      })
      result.get
    }

    if (location == null) return null
    val refType = location.declaringType
    if (refType == null) return null

    val scriptFile = findScriptFile(location)
    if (scriptFile.isDefined) return scriptFile.get

    val qName = qualName(refType)

    if (!ScalaMacroDebuggingUtil.isEnabled)
      findClassByQualName(qName).map(_.getNavigationElement.getContainingFile).orNull
    else
      searchForMacroDebugging(qName)
  }

  private def nameMatches(elem: PsiElement, refType: ReferenceType): Boolean = {
    val pattern = NamePattern.forElement(elem)
    pattern != null && pattern.matches(refType)
  }

  private def findPsiClassByReferenceType(refType: ReferenceType): Option[PsiElement] = {
    def checkLines(elem: PsiElement, document: Document) = {
      val refTypeLines = refType.allLineLocations().asScala.map(_.lineNumber() - 1).toSet
      val startLine = document.getLineNumber(elem.getTextRange.getStartOffset)
      val endLine = document.getLineNumber(elem.getTextRange.getEndOffset)
      val docLines = Range.inclusive(startLine, endLine).toSet
      refTypeLines.intersect(docLines).nonEmpty //very loose check because sometimes first line for <init> method is after range of the class
    }

    try {
      val project = getDebugProcess.getProject
      val location = refType.allLineLocations().get(0)
      val file = getPsiFileByLocation(project, location)
      val document = PsiDocumentManager.getInstance(project).getDocument(file)

      file.depthFirst.collectFirst {
        case elem if ScalaEvaluatorBuilderUtil.isGenerateClass(elem) && checkLines(elem, document) && nameMatches(elem, refType) => elem
      }

    }
    catch {
      case t: Throwable => None
    }
  }
}

object ScalaPositionManager {
  private val LOG: Logger = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl")
  private val SCRIPT_HOLDER_CLASS_NAME: String = "Main$$anon$1"

  def positionsOnLine(file: PsiFile, lineNumber: Int): Seq[PsiElement] = {
    val scFile = file match {
      case sf: ScalaFile => sf
      case _ => return Seq.empty
    }
    val cacheProvider = new CachedValueProvider[mutable.HashMap[Int, Seq[PsiElement]]] {
      override def compute(): Result[mutable.HashMap[Int, Seq[PsiElement]]] = Result.create(mutable.HashMap[Int, Seq[PsiElement]](), file)
    }

    CachedValuesManager.getCachedValue(file, cacheProvider).getOrElseUpdate(lineNumber, positionsOnLineInner(scFile, lineNumber))
  }

  private def positionsOnLineInner(file: ScalaFile, lineNumber: Int): Seq[PsiElement] = {
    inReadAction {
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
      if (lineNumber >= document.getLineCount) return Seq.empty
      val startLine = document.getLineStartOffset(lineNumber)
      val endLine = document.getLineEndOffset(lineNumber)

      def elementsOnTheLine(file: ScalaFile, lineNumber: Int): Seq[PsiElement] = {
        val result = ArrayBuffer[PsiElement]()
        var elem = file.findElementAt(startLine)

        while (elem != null && elem.getTextOffset <= endLine) {
          result += elem
          elem = PsiTreeUtil.nextLeaf(elem, true)
        }
        result
      }

      def findParent(element: PsiElement): Option[PsiElement] = {
        val parentsOnTheLine = element.parents.takeWhile(e => e.getTextOffset > startLine).toIndexedSeq
        val lambda = parentsOnTheLine.find(isLambda)
        val maxExpressionPatternOrTypeDef = parentsOnTheLine.reverse.find {
          case _: ScExpression => true
          case _: ScConstructorPattern | _: ScInfixPattern => true
          case _: ScTypeDefinition => true
          case _ => false
        }
        Seq(lambda, maxExpressionPatternOrTypeDef).flatten.sortBy(_.getTextLength).headOption
      }

      elementsOnTheLine(file, lineNumber).flatMap(findParent).distinct
    }
  }

  def isLambda(element: PsiElement) = element match {
    case _: ScTypeDefinition => false
    case e if ScalaEvaluatorBuilderUtil.isGenerateClass(e) => true
    case _ => false
  }

  def lambdasOnLine(file: PsiFile, lineNumber: Int): Seq[PsiElement] = {
    positionsOnLine(file, lineNumber).filter(isLambda)
  }

  private def getSpecificNameForDebugger(td: ScTypeDefinition): String = {
    val name = td.getQualifiedNameForDebugger

    td match {
      case _: ScObject => s"$name$$"
      case _: ScTrait => s"$name$$class"
      case _ => name
    }
  }

  private class MyClassPrepareRequestor(position: SourcePosition, requestor: ClassPrepareRequestor) extends ClassPrepareRequestor {
   private val sourceName = position.getFile.getName
   private def sourceNameOf(refType: ReferenceType): Option[String] = Try(refType.sourceName()).toOption

   def processClassPrepare(debuggerProcess: DebugProcess, referenceType: ReferenceType) {
      val positionManager: CompoundPositionManager = debuggerProcess.asInstanceOf[DebugProcessImpl].getPositionManager

     if (!sourceNameOf(referenceType).contains(sourceName)) return

      if (positionManager.locationsOfLine(referenceType, position).size > 0) {
        requestor.processClassPrepare(debuggerProcess, referenceType)
      }
      else {
        val positionClasses: util.List[ReferenceType] = positionManager.getAllClasses(position)
        if (positionClasses.contains(referenceType)) {
          requestor.processClassPrepare(debuggerProcess, referenceType)
        }
      }
    }
  }

  private class NamePattern(elem: PsiElement) {
    private val classJVMNameParts: Seq[String] = {
      val forElem = partsFor(elem).toIterator
      val forParents = elem.parentsInFile.flatMap(e => partsFor(e).map(_ + "$"))
      (forElem ++ forParents).toSeq.reverse
    }

    private def partsFor(elem: PsiElement): Seq[String] = {
      elem match {
        case newTd: ScNewTemplateDefinition if DebuggerUtil.generatesAnonClass(newTd) => Seq("$anon")
        case newTd: ScNewTemplateDefinition => Seq.empty
        case td: ScTypeDefinition => Seq(ScalaNamesUtil.toJavaName(td.name))
        case _ if ScalaEvaluatorBuilderUtil.isGenerateClass(elem) =>
          (1 to ScalaEvaluatorBuilderUtil.anonClassCount(elem)).map(_ => "$anonfun")
        case _ => Seq.empty
      }
    }

    def matches(refType: ReferenceType): Boolean = {
      val name = refType.name()

      def checkParts(): Boolean = {
        var nameTail = name
        for (part <- classJVMNameParts) {
          val index = nameTail.indexOf(part)
          if (index >= 0) {
            nameTail = nameTail.substring(index + part.length)
          }
          else return false
        }
        nameTail.indexOf("$anon") == -1
      }

      elem match {
        case td: ScTypeDefinition if !DebuggerUtil.isLocalClass(td) =>
          val qName = getSpecificNameForDebugger(td)
          name == qName
        case _ => checkParts()
      }
    }
  }

  private object NamePattern {
    def forElement(elem: PsiElement): NamePattern = {
      if (!ScalaEvaluatorBuilderUtil.isGenerateClass(elem)) return null

      val cacheProvider = new CachedValueProvider[NamePattern] {
        override def compute(): Result[NamePattern] = Result.create(new NamePattern(elem), elem)
      }

      CachedValuesManager.getCachedValue(elem, cacheProvider)
    }
  }

}
