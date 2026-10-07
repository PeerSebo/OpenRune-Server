package org.rsmod.content.skills.thieving.configs

object ThievingPhrasing {
    private val OVERRIDES = mapOf("silk" to "a piece of silk")

    private val MASS_NOUNS = setOf("bread", "coal", "flour", "ashes", "rope", "hay")

    private val SINGULAR_ENDING_IN_S = setOf("compass", "molasses", "papyrus")

    fun nounPhrase(name: String): String {
        OVERRIDES[name]?.let {
            return it
        }
        if (isUncountable(name)) {
            return "some $name"
        }
        return "${article(name)} $name"
    }

    private fun isUncountable(name: String): Boolean {
        if (name in MASS_NOUNS || name.endsWith(" ore")) {
            return true
        }
        return name.endsWith("s") && name !in SINGULAR_ENDING_IN_S
    }

    private fun article(name: String): String =
        if (name.firstOrNull()?.lowercaseChar() in VOWELS) "an" else "a"

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
}
