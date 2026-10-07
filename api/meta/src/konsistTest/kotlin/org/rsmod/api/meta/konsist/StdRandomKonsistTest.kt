package org.rsmod.api.meta.konsist

import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.declaration.KoVariableDeclaration
import com.lemonappdev.konsist.api.ext.list.getters
import com.lemonappdev.konsist.api.ext.list.parameters
import com.lemonappdev.konsist.api.ext.list.setters
import com.lemonappdev.konsist.api.ext.list.variables
import com.lemonappdev.konsist.api.provider.KoNonNullableTypeProvider
import com.lemonappdev.konsist.api.provider.KoNullableTypeProvider
import com.lemonappdev.konsist.api.provider.KoTacitTypeProvider
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.core.exception.KoInternalException
import kotlin.random.Random
import org.junit.jupiter.api.Test

class StdRandomKonsistTest {
    @Test
    fun `avoid usage of stdlib Random imports`() {
        val imports = KonsistScope.imports
        imports.assertFalse(additionalMessage = ERROR_MESSAGE) {
            it.name == "java.util.concurrent.ThreadLocalRandom"
        }
    }

    @Test
    fun `avoid usage of stdlib Random in properties`() {
        val properties = KonsistScope.properties(includeNested = true)
        properties.assertFalse(additionalMessage = ERROR_MESSAGE) { it.isOrUsesStdRandom() }
    }

    // NOTE: Due to limitations in Konsist's ability to fully analyze getter return types and
    // receiver types, it is currently possible for getters to bypass this test by returning direct
    // Random values (e.g., `(0..1).random()`). As an alternative, we could enforce stricter checks
    // by not verifying the receiver type (e.g., IntRange or standard Collections) and instead
    // outright forbidding any use of `random()` and `return random()` in getters. However, for now,
    // I have decided against this as it can intervene with non-Random related code.

    @Test
    fun `avoid usage of stdlib Random in getter properties`() {
        val getters = KonsistScope.properties(includeNested = true).getters
        getters.variables.assertFalse(additionalMessage = ERROR_MESSAGE) { it.isOrUsesStdRandom() }
    }

    @Test
    fun `avoid usage of stdlib Random in setter properties`() {
        val setters = KonsistScope.properties(includeNested = true).setters
        setters.variables.assertFalse(additionalMessage = ERROR_MESSAGE) { it.isOrUsesStdRandom() }
    }

    @Test
    fun `avoid usage of stdlib Random in functions`() {
        val scopeFunctions = KonsistScope.functions(includeNested = true, includeLocal = true)
        val variables = scopeFunctions.variables
        val parameters = scopeFunctions.parameters.discardSuspendLambdas()
        val functions = scopeFunctions.discardSuspendReturnBlocks()
        variables.assertFalse(additionalMessage = ERROR_MESSAGE) { it.isOrUsesStdRandom() }
        functions.assertFalse(additionalMessage = ERROR_MESSAGE) { it.containsStdRandomUsage() }
        parameters.assertFalse(additionalMessage = ERROR_MESSAGE) { it.isRandomReference() }
    }

    @Test
    fun `avoid usage of stdlib Random in parameters`() {
        val functions = KonsistScope.functions(includeNested = true, includeLocal = true)
        val parameters = functions.parameters.discardSuspendLambdas()
        parameters.assertFalse(additionalMessage = ERROR_MESSAGE) { it.isRandomReference() }
    }

    private companion object {
        private const val RANDOM_GENERATION_ERROR_MESSAGE =
            "Random number generation should come from a " +
                "[org.rsmod.api.random.GameRandom] implementation."

        private val ERROR_MESSAGE =
            """
            | - $RANDOM_GENERATION_ERROR_MESSAGE
            """
                .trimMargin()
    }
}

private fun KoVariableDeclaration.isRandomReference(): Boolean =
    hasRandomType() || hasRandomTacitType()

private fun KoPropertyDeclaration.isRandomReference(): Boolean =
    hasRandomType() || hasRandomTacitType()

private fun KoParameterDeclaration.isRandomReference(): Boolean = hasRandomType()

private fun KoNullableTypeProvider.hasRandomType(): Boolean =
    hasTypeOf(Random::class) ||
        hasTypeOf(java.util.Random::class) ||
        hasTypeOf(java.util.concurrent.ThreadLocalRandom::class)

private fun KoTacitTypeProvider.hasRandomTacitType(): Boolean =
    hasTacitTypeOf(Random::class) ||
        hasTacitTypeOf(java.util.Random::class) ||
        hasTacitTypeOf(java.util.concurrent.ThreadLocalRandom::class)

private fun KoNonNullableTypeProvider.hasRandomType(): Boolean =
    hasTypeOf(Random::class) ||
        hasTypeOf(java.util.Random::class) ||
        hasTypeOf(java.util.concurrent.ThreadLocalRandom::class)

private val NAIVE_RANDOM_CALL_REGEX = Regex(""".+\.random\(\)""")
private val NAIVE_RANDOM_RETURN_NO_BODY_REGEX =
    Regex("""[^\n]*=\s*(?:[A-Za-z_]\w*\.)*(?:Random|ThreadLocalRandom)\b""")
private val NAIVE_RANDOM_RETURN_WITH_BODY_REGEX =
    Regex("""\breturn\b[^\n]*\b(?:[A-Za-z_]\w*\.)*(?:Random|ThreadLocalRandom)\b""")
private val NAIVE_RANDOM_EXTENSION_RETURN_NO_BODY_REGEX = Regex(""".*= random\(\)""")
private val NAIVE_RANDOM_EXTENSION_RETURN_WITH_BODY_REGEX = Regex(""".*return random\(\)""")

private fun KoVariableDeclaration.isOrUsesStdRandom(): Boolean =
    isRandomReference() || value?.matches(NAIVE_RANDOM_CALL_REGEX) == true

private fun KoPropertyDeclaration.isOrUsesStdRandom(): Boolean {
    val isRandomReference = isRandomReference()
    if (isRandomReference) {
        return true
    }
    val callsStdRandom = value?.matches(NAIVE_RANDOM_CALL_REGEX) == true
    if (callsStdRandom) {
        return true
    }
    return text.contains(NAIVE_RANDOM_RETURN_NO_BODY_REGEX) ||
        text.contains(NAIVE_RANDOM_RETURN_WITH_BODY_REGEX)
}

private fun KoFunctionDeclaration.containsStdRandomUsage(): Boolean {
    if (text.contains(NAIVE_RANDOM_CALL_REGEX)) {
        return true
    }
    if (isValidExtensionReturningBasicType()) {
        if (extensionContainsRandomReturnStatement()) {
            return true
        }
    }
    return containsRandomReturnStatement()
}

private fun KoFunctionDeclaration.isValidExtensionReturningBasicType(): Boolean {
    // KoFunctionDeclaration may not always be able to infer the return type.
    /* https://docs.konsist.lemonappdev.com/features/compiler-type-inference */
    val returnsBasicType = !hasReturnType() || returnType?.isKotlinBasicType == true
    val isCollectionExtension = receiverType?.isKotlinCollectionType == true
    val isIntRangeExtension = receiverType?.name == "IntRange"
    val isValidExtensionReceiver = isCollectionExtension || isIntRangeExtension
    return returnsBasicType && isValidExtensionReceiver
}

private fun KoFunctionDeclaration.extensionContainsRandomReturnStatement(): Boolean =
    if (hasBlockBody) {
        text.contains(NAIVE_RANDOM_EXTENSION_RETURN_WITH_BODY_REGEX)
    } else {
        text.contains(NAIVE_RANDOM_EXTENSION_RETURN_NO_BODY_REGEX)
    }

private fun KoFunctionDeclaration.containsRandomReturnStatement(): Boolean =
    if (hasBlockBody) {
        text.contains(NAIVE_RANDOM_RETURN_WITH_BODY_REGEX)
    } else {
        text.contains(NAIVE_RANDOM_RETURN_NO_BODY_REGEX)
    }

/**
 * Konsist cannot parse suspend-function parameters; exclude them until the parser supports
 * them.
 */
private fun List<KoParameterDeclaration>.discardSuspendLambdas(): List<KoParameterDeclaration> {
    val valid = mutableListOf<KoParameterDeclaration>()
    for (parameter in this) {
        try {
            parameter.hasTypeOf(Unit::class)
            valid += parameter
        } catch (_: KoInternalException) {
        }
    }
    return valid
}

private fun List<KoFunctionDeclaration>.discardSuspendReturnBlocks(): List<KoFunctionDeclaration> {
    val valid = mutableListOf<KoFunctionDeclaration>()
    for (parameter in this) {
        try {
            parameter.returnType?.isKotlinBasicType
            valid += parameter
        } catch (_: KoInternalException) {
        }
    }
    return valid
}
