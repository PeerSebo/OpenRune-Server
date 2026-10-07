package org.rsmod.api.farming

public enum class FarmingPatchType {
    ALLOTMENT,
    FLOWER,
    HERB,
    HOPS,
    BUSH,
    TREE,
    FRUIT_TREE;

    private val definition: FarmingWorldData.PatchType
        get() = FarmingWorldData.patchTypes.getValue(name)

    public val seedFamilyParam: Int
        get() = definition.seedFamily

    public val waterable: Boolean
        get() = definition.waterable

    public val cure: FarmingCureMethod
        get() = FarmingCureMethod.valueOf(definition.cure)

    public val checkHealth: Boolean
        get() = definition.checkHealth

    public val bearsProduce: Boolean
        get() = definition.bearsProduce

    public val emptyLocName: String
        get() = definition.emptyLocName

    public val osrsWeedCycleMinutes: Int
        get() = definition.weedCycleMinutes

    public val weedCycleMinutes: Int
        get() = maxOf(1, osrsWeedCycleMinutes / FarmingConstants.GROWTH_SPEED_MULTIPLIER)

    public companion object {
        private val bySeedFamily: Map<Int, FarmingPatchType> by lazy {
            entries.associateBy(FarmingPatchType::seedFamilyParam)
        }

        public fun ofSeedFamily(param709: Int): FarmingPatchType? = bySeedFamily[param709]
    }
}

public enum class FarmingCureMethod {
    CURE,
    PRUNE,
}
