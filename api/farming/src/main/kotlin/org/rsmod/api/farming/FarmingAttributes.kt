package org.rsmod.api.farming

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

public object FarmingAttributes {
    /** Only FarmingPipeline owns this persistence key; another AttributeKey would overwrite its row. */
    public const val PATCHES_PERSISTENCE_KEY: String = "farming_patches"

    /** FarmingPipeline encodes live patches and bins together; these objects must not auto-persist. */
    public val PATCHES: AttributeKey<FarmingPatches> = AttributeKey(temp = true)

    public val BINS: AttributeKey<FarmingBins> = AttributeKey(temp = true)

    internal val VISIBLE: AttributeKey<FarmingVisiblePatches> = AttributeKey(temp = true)
}

internal class FarmingVisiblePatches(
    val buildAreaPacked: Int,
    val patches: List<FarmingPatchDef>,
    val bins: List<FarmingBinDef>,
) {
    val isEmpty: Boolean
        get() = patches.isEmpty() && bins.isEmpty()
}

public fun Player.farmingPatches(): FarmingPatches? = attr[FarmingAttributes.PATCHES]

public fun Player.farmingPatchesOrCreate(): FarmingPatches =
    attr.getOrPut(FarmingAttributes.PATCHES) { FarmingPatches() }

public fun Player.farmingBins(): FarmingBins? = attr[FarmingAttributes.BINS]

public fun Player.farmingBinsOrCreate(): FarmingBins =
    attr.getOrPut(FarmingAttributes.BINS) { FarmingBins() }
