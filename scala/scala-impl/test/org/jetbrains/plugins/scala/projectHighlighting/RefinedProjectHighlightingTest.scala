package org.jetbrains.plugins.scala.projectHighlighting

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.HighlightingTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[HighlightingTests]))
class RefinedProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {
  override def githubUsername = "fthomas"

  override def githubRepoName = "refined"

  override def revision = "170d0187b9a4ffbfb3578de7646d79aa8e5fd4ed"

  override def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "RefTypeSpecScalazMonadError.scala" -> Set((1593, 1620)),
    "MaxSpec.scala" -> Set((2600, 2648)),
    "RefTypeSpec.scala" -> Set((2592, 2619),(2690, 2738),(2886, 2935)),
    "GenericInferenceSpec.scala" -> Set((655, 698),(767, 810)),
    "Validate.scala" -> Set((1702, 1703)),
    "ImplicitScopeSpec.scala" -> Set((1068, 1129)),
    "CatsSpec.scala" -> Set((448, 451),(423, 426)),
    "any.scala" -> Set((715, 764)),
    "InferMacro.scala" -> Set((656, 696),(699, 700),(477, 492)),
    "collection.scala" -> Set((1357, 1467),(1326, 1350),(2043, 2100),(2011, 2036)),
    "RefinedType.scala" -> Set((863, 865),(834, 860)),
    "auto.scala" -> Set((487, 489),(1897, 1899),(2306, 2313),(2695, 2697)),
    "boolean.scala" -> Set((562, 601),(604, 605)),
    "BooleanValidateSpec.scala" -> Set((5079, 5137),(5255, 5316),(5466, 5515)),
    "CollectionValidateSpec.scala" -> Set((711, 742),(821, 857)),
    "RefineMacro.scala" -> Set((1147, 1184),(1187, 1188),(657, 672)),
    "string.scala" -> Set((660, 725),(888, 953),(1527, 1607)),
    "Refined.scala" -> Set((599, 613),(603, 610)),
    "reftype.scala" -> Set((789, 803)),
    "RefineSyntaxSpec.scala" -> Set(),
    "char.scala" -> Set(),
    "generic.scala" -> Set((454, 491)),
    "RefTypeSpecScalazTag.scala" -> Set(),
    "numeric.scala" -> Set(),
    "RefTypeSpecScalazContravariant.scala" -> Set((1386, 1413)),
    "InferJavapSpec.scala" -> Set(),
    "RefineMSpec.scala" -> Set(),
    "NumericInferenceSpec.scala" -> Set(),
    "MinSpec.scala" -> Set((2271, 2304))
  )
}
