package org.rsmod.api.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/**
 * Transform tables include filler and fallback entries; only named reachable crop states are
 * valid render targets.
 */
public class FarmingTransformTable
internal constructor(public val locId: Int, private val transforms: IntArray) {
    public val filler: Int = transforms.getOrElse(FarmingConstants.WEEDS_TRANSFORM_INDEX) { -1 }

    public val size: Int
        get() = transforms.size

    public fun at(value: Int): Int = transforms.getOrElse(value) { -1 }

    /** Use the first occurrence: repeated mature locs are padding after the block anchor. */
    public fun indexOf(locId: Int): Int = transforms.indexOfFirst { it == locId }

    public fun isFiller(value: Int): Boolean {
        if (value <= FarmingConstants.WEEDS_TRANSFORM_INDEX) {
            return false
        }
        if (value > FarmingConstants.MAX_TRANSFORM_INDEX || value >= transforms.size) {
            return true
        }
        val loc = transforms[value]
        return loc == -1 || loc == filler
    }

    public companion object {
        public fun of(locId: Int): FarmingTransformTable? {
            val type = ServerCacheManager.getObject(locId) ?: return null
            val transforms = type.multiLoc
            if (transforms.isEmpty()) {
                return null
            }
            return FarmingTransformTable(locId, transforms)
        }

        public fun locIdOrNull(locName: String): Int? =
            runCatching { locName.asRSCM(RSCMType.LOC) }.getOrNull()?.takeIf { it >= 0 }
    }
}
