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
    "MaxSpec.scala" -> Set(),
    "RefTypeSpec.scala" -> Set((2690, 2738), (1353, 1407), (2886, 2935), (1388, 1389), (2592, 2619)),
    "GenericInferenceSpec.scala" -> Set((655, 698), (767, 810)),
    "Validate.scala" -> Set((1702, 1703), (1697, 1698), (1695, 1696)),
    "ImplicitScopeSpec.scala" -> Set((1068, 1129)),
    "package.scala" -> Set((701, 712), (889, 902)),
    "CatsSpec.scala" -> Set((448, 451), (423, 426)),
    "any.scala" -> Set((715, 764), (690, 708)),
    "InferMacro.scala" -> Set((656, 696), (699, 700), (477, 492)),
    "collection.scala" -> Set(),
    "RefinedType.scala" -> Set((863, 865), (834, 860)),
    "auto.scala" -> Set((1897, 1899), (2306, 2313), (2695, 2697), (487, 489)),
    "boolean.scala" -> Set(),
    "BooleanValidateSpec.scala" -> Set((5079, 5137), (5255, 5316), (5466, 5515)),
    "CollectionValidateSpec.scala" -> Set((711, 742), (821, 857)),
    "RefineMacro.scala" -> Set((1147, 1184), (1187, 1188), (657, 672)),
    "string.scala" -> Set(),
    "NumericArbitrarySpec.scala" -> Set(),
    "Refined.scala" -> Set((599, 613), (603, 610), (579, 592)),
    "reftype.scala" -> Set((789, 803)),
    "RefineSyntaxSpec.scala" -> Set(),
    "RefType.scala" -> Set(),
    "char.scala" -> Set(),
    "generic.scala" -> Set((454, 491), (422, 447)),
    "RefTypeSpecScalazTag.scala" -> Set(),
    "byteVector.scala" -> Set((493, 496), (491, 492)),
    "numeric.scala" -> Set(),
    "RefTypeSpecScalazContravariant.scala" -> Set((1386, 1413)),
    "InferJavapSpec.scala" -> Set(),
    "RefineMSpec.scala" -> Set(),
    "NumericInferenceSpec.scala" -> Set(),
    "MinSpec.scala" -> Set((2023, 2059), (2055, 2056), (2610, 2611), (2737, 2798), (2271, 2304), (2785, 2786), (2570, 2623))
  )
}
