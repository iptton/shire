package com.phodal.shirelang.compiler.hobbit

import com.intellij.openapi.diagnostic.logger

sealed class PatternFun(open val regex: String) {
    class Prompt(val message: String): PatternFun("prompt")
    class Grep(vararg val patterns: String): PatternFun("grep")
    class Sort(vararg val arguments: String): PatternFun("sort")
    class Xargs(vararg val variables: String): PatternFun("xargs")
    companion object {
        fun from(value: FrontMatterType): List<PatternFun> {
            return when (value) {
                is FrontMatterType.STRING -> {
                    return listOf(Prompt(value.value as? String ?: ""))
                }
                is FrontMatterType.PATTERN -> {
                    val action = value.value as? ShirePatternAction
                    action?.processors ?: emptyList()
                }
                else -> {
                    logger<PatternFun>().error("Unknown pattern processor type: $value")
                    emptyList()
                }
            }
        }
    }
}

/**
 * The action location of the action.
 */
class ShirePatternAction(val pattern: String, val processors: List<PatternFun>)


sealed class FrontMatterType(val value: Any) {
    class STRING(value: String): FrontMatterType(value)
    class NUMBER(value: Int): FrontMatterType(value)
    class DATE(value: String): FrontMatterType(value)
    class BOOLEAN(value: Boolean): FrontMatterType(value)
    class ARRAY(value: List<FrontMatterType>): FrontMatterType(value)
    class OBJECT(value: Map<String, FrontMatterType>): FrontMatterType(value)

    /**
     * The default pattern action handles for processing
     */
    class PATTERN(value: ShirePatternAction): FrontMatterType(value)

    /**
     * The case match for the front matter.
     */
    class CaseMatch(value: Map<String, PATTERN>): FrontMatterType(value)
}
