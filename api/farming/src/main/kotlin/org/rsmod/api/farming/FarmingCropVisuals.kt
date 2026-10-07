package org.rsmod.api.farming

/**
 * A contiguous anchor uses base-plus-offset; interleaved crop bands must name each offset
 * explicitly.
 */
public data class FarmingAnchor(
    public val loc: String,
    public val stage: Int = 0,
    public val stageLocs: List<String> = emptyList(),
) {
    init {
        require(loc.isNotBlank()) { "Farming anchor loc must not be blank." }
        require(stage >= 0) { "Farming anchor stage cannot be negative: $loc" }
    }

    public val isPerStage: Boolean
        get() = stageLocs.isNotEmpty()

    public companion object {
        public fun stages(locs: List<String>, firstStage: Int = 0): FarmingAnchor {
            require(locs.isNotEmpty()) { "Per-stage farming anchor needs at least one loc." }
            return FarmingAnchor(loc = locs.first(), stage = firstStage, stageLocs = locs)
        }
    }
}

/**
 * Visuals are named cache anchors, not universal band offsets. Disease/death and
 * checked/unchecked blocks vary by crop family.
 */
public data class FarmingCropVisuals(
    public val growing: FarmingAnchor,
    public val watered: FarmingAnchor? = null,
    public val diseased: FarmingAnchor? = null,
    public val dead: FarmingAnchor? = null,
    public val unchecked: FarmingAnchor? = null,
    /** Checked mature states carry harvest ops and use produce count as the offset. */
    public val grownChecked: FarmingAnchor? = null,
    public val stump: FarmingAnchor? = null,
) {
    internal fun anchors(): List<Pair<String, FarmingAnchor>> = buildList {
        add(GROWING to growing)
        watered?.let { add(WATERED to it) }
        diseased?.let { add(DISEASED to it) }
        dead?.let { add(DEAD to it) }
        unchecked?.let { add(UNCHECKED to it) }
        grownChecked?.let { add(GROWN_CHECKED to it) }
        stump?.let { add(STUMP to it) }
    }

    internal companion object {
        const val GROWING: String = "growing"
        const val WATERED: String = "watered"
        const val DISEASED: String = "diseased"
        const val DEAD: String = "dead"
        const val UNCHECKED: String = "unchecked"
        const val GROWN_CHECKED: String = "grownChecked"
        const val STUMP: String = "stump"
    }
}
