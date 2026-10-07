package org.rsmod.api.meta.konsist

import com.lemonappdev.konsist.api.declaration.KoKDocDeclaration
import com.lemonappdev.konsist.api.ext.list.tagprovider.withSeeTags
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SeeTagKonsistTest {
    @Test
    fun `test see tag references are formatted correctly`() {
        val docs = KonsistScope.allKDocs().validateSeeTagParsing()
        docs.withSeeTags().assertTrue(additionalMessage = SEE_TAG_FORMAT_VALIDATION_ERROR_MESSAGE) {
            it.seeTags.all { tag -> tag.value.isValidSeeTagFormat() }
        }
    }

    /**
     * Konsist throws on unbracketed see tags; this test rejects them before other architecture
     * checks run.
     */
    private fun List<KoKDocDeclaration>.validateSeeTagParsing(): List<KoKDocDeclaration> {
        for (declaration in this) {
            assertDoesNotThrow(
                message = { "$SEE_TAG_PARSING_ERROR_MESSAGE\n\nFile: ${declaration.location}\n\n" },
                executable = { declaration.seeTags },
            )
        }
        return this
    }

    private companion object {
        private val SEE_TAG_FORMAT_VALIDATION_ERROR_MESSAGE: String =
            """
                |   @see references must be written within square brackets.
                |   Example: `@see [org.foo.FooClass]`
            """
                .trimMargin()

        private val SEE_TAG_PARSING_ERROR_MESSAGE: String =
            """
                Illegal @see tag declaration - tag must be followed by a valid reference!
                Example: `@see [org.bar.BarClass]`
            """
                .trimIndent()
    }
}

private fun String.isValidSeeTagFormat(): Boolean =
    isBlank() || isValidSeeTagReference() || isValidSeeTagHyperlink()

private fun String.isValidSeeTagReference(): Boolean = first() == '[' && last() == ']'

private fun String.isValidSeeTagHyperlink(): Boolean = startsWith("<img")
