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
    "RefTypeSpec.scala" -> Set(),
    "GenericArbitrarySpec.scala" -> Set((322, 377),(374, 375),(368, 373)),
    "GenericValidateSpec.scala" -> Set(),
    "RefTypeRedSpec.scala" -> Set((411, 434),(620, 622),(932, 933)),
    "GenericInferenceSpec.scala" -> Set(),
    "Validate.scala" -> Set((1702, 1703),(1697, 1698),(1695, 1696)),
    "ImplicitScopeSpec.scala" -> Set(),
    "StringValidateSpec.scala" -> Set(),
    "CollectionArbitrarySpec.scala" -> Set(),
    "StringUtilSpecJvm.scala" -> Set(),
    "package.scala" -> Set((701, 712),(889, 902),(984, 991)),
    "CatsSpec.scala" -> Set((448, 451),(423, 426)),
    "any.scala" -> Set((715, 764),(690, 708)),
    "RefinedSpec.scala" -> Set(),
    "ScalazSpec.scala" -> Set((776, 805),(745, 746),(731, 734),(740, 744),(735, 736)),
    "InferMacro.scala" -> Set((656, 696),(699, 700),(477, 492)),
    "NumericValidateSpec.scala" -> Set(),
    "collection.scala" -> Set(),
    "RefinedType.scala" -> Set((863, 865),(834, 860)),
    "StringUtilSpec.scala" -> Set(),
    "auto.scala" -> Set(),
    "RefineJavapSpec.scala" -> Set(),
    "eval.scala" -> Set(),
    "boolean.scala" -> Set(),
    "BooleanValidateSpec.scala" -> Set(),
    "net.scala" -> Set(),
    "ByteVectorValidateSpec.scala" -> Set(),
    "EvalValidateSpec.scala" -> Set(),
    "StringTypesSpec.scala" -> Set(),
    "CollectionValidateSpec.scala" -> Set(),
    "SyntaxSpec.scala" -> Set((737, 740),(746, 750),(751, 752),(741, 742),(782, 811)),
    "RefineMacro.scala" -> Set(),
    "string.scala" -> Set(),
    "CharArbitrarySpec.scala" -> Set((1043, 1048),(1067, 1115),(1049, 1050),(1060, 1061),(1054, 1059)),
    "StringInferenceSpec.scala" -> Set(),
    "RefTypeMonadErrorSpec.scala" -> Set((2247, 2262)),
    "time.scala" -> Set(),
    "NumericArbitrarySpec.scala" -> Set(),
    "Refined.scala" -> Set((599, 613),(603, 610)),
    "RefTypeConfigConvertSpec.scala" -> Set((594, 595),(2097, 2098)),
    "reftype.scala" -> Set((789, 803)),
    "digests.scala" -> Set(),
    "RefineSyntaxSpec.scala" -> Set(),
    "RefineVBenchmark.scala" -> Set((500, 520)),
    "BigLiteralsSpec.scala" -> Set(),
    "TypeableSpec.scala" -> Set((1252, 1265),(847, 875),(1082, 1095),(1096, 1097),(820, 821),(1266, 1267),(806, 819)),
    "CollectionInferenceSpec.scala" -> Set(),
    "RefType.scala" -> Set(),
    "char.scala" -> Set(),
    "generic.scala" -> Set(),
    "BooleanInferenceSpec.scala" -> Set(),
    "NetTypesSpec.scala" -> Set(),
    "RefTypeSpecScalazTag.scala" -> Set(),
    "StringArbitrarySpec.scala" -> Set(),
    "byteVector.scala" -> Set((493, 496),(491, 492)),
    "numeric.scala" -> Set(),
    "AutoSpec.scala" -> Set(),
    "RefTypeSpecScalazContravariant.scala" -> Set((1386, 1413)),
    "InferJavapSpec.scala" -> Set(),
    "RefineMSpec.scala" -> Set(),
    "NumericInferenceSpec.scala" -> Set(),
    "MinSpec.scala" -> Set(),
    "RefTypeCodecSpec.scala" -> Set((595, 596),(598, 604),(842, 843)),
    "AnyArbitrarySpec.scala" -> Set((371, 440),(437, 438),(427, 436))
  )

}
