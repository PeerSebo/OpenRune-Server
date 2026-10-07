package org.rsmod.api.farming

/** Extra lives apply only to crops with harvest lives. Disease reduction uses ceiling division. */
public enum class FarmingCompost {
    NONE,
    COMPOST,
    SUPERCOMPOST,
    ULTRACOMPOST;

    private val definition: FarmingWorldData.CompostType
        get() = FarmingWorldData.compostTypes.getValue(name)

    public val extraLives: Int
        get() = definition.extraLives

    public val diseaseDivisor: Int
        get() = definition.diseaseDivisor

    public companion object {
        public fun find(name: String?): FarmingCompost? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
