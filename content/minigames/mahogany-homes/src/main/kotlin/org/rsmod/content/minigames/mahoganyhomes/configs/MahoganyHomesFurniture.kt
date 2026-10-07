package org.rsmod.content.minigames.mahoganyhomes.configs

public enum class MahoganyHomesTier {
    BEGINNER,
    NOVICE,
    ADEPT,
    EXPERT;

    private val definition get() = MahoganyHomesData.tiers.getValue(name)
    public val levelReq: Int get() = definition.level
    public val plankObj: String get() = definition.plank
    public val buildXp: Double get() = definition.buildXpTenths / 10.0
    public val repairXp: Double get() = definition.repairXpTenths / 10.0
    public val completionXp: Double get() = definition.completionXpTenths / 10.0
    public val points: Int get() = definition.points

    public val barRepairXp: Double
        get() = BAR_REPAIR_XP

    public companion object {
        public const val BAR_REPAIR_XP: Double = 120.0

        // Ordinal order matches the two-bit tier encoding.
        public fun ofOrdinal(ordinal: Int): MahoganyHomesTier = entries[ordinal]
    }
}

// Normalize cache furniture aliases before applying costs; unknown kinds must fail validation.
public object MahoganyHomesFurniture {

    private val REPAIR_PLANK_KINDS = setOf("clock", "hatstand", "mirror")

    private val REPAIR_BAR_KINDS = setOf("range", "sink", "bath")

    private val KIND_ALIASES =
        mapOf(
            "red" to "bed",
            "grandfatherclock" to "clock",
            "fai_varrock_posh_grandfather_clock" to "clock",
            "bathmirror" to "mirror",
            "fai_varrock_mirror" to "mirror",
        )

    private fun normalize(kind: String): String {
        val k = kind.lowercase()
        return KIND_ALIASES[k] ?: k
    }

    public fun buildPlankCost(kind: String): Int? {
        val k = normalize(kind)
        return when {
            k in REPAIR_PLANK_KINDS || k in REPAIR_BAR_KINDS -> null
            k.contains("chair") -> 1
            // Match large_table/longtable before the generic table substring.
            k.contains("large") && (k.contains("table") || k == "longtable") -> 4
            k == "longtable" -> 4
            k.contains("small") && k.contains("table") -> 2
            k == "sidetable" -> 2
            k.contains("drawer") -> 2
            k.contains("cabinet") -> 2
            k.contains("bookcase") -> 2
            k.contains("shelves") -> 2
            k.contains("cupboard") -> 2
            k.contains("dresser") -> 2
            k.contains("wardrobe") -> 2
            k.contains("table") -> 3
            k.contains("bed") -> 3
            else -> null
        }
    }

    public fun repairPlankCost(kind: String): Int? =
        if (normalize(kind) in REPAIR_PLANK_KINDS) 1 else null

    public fun repairsWithBar(kind: String): Boolean = normalize(kind) in REPAIR_BAR_KINDS
}
