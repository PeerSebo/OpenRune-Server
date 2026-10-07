package org.rsmod.api.farming

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

/**
 * Cache scene membership by build area, not player tile. Instances can change contents at
 * identical coordinates and must bypass this cache.
 */
@Singleton
public class FarmingSceneIndex
@Inject
constructor(
    private val locReg: LocRegistry,
    private val patches: FarmingPatchRegistry,
    private val bins: FarmingBinRegistry,
) {
    private val logger = InlineLogger()

    private val cache = HashMap<Int, Scan>()

    public fun visiblePatches(buildArea: CoordGrid): List<FarmingPatchDef> =
        scan(buildArea)?.patches ?: emptyList()

    public fun visibleBins(buildArea: CoordGrid): List<FarmingBinDef> =
        scan(buildArea)?.bins ?: emptyList()

    private fun scan(buildArea: CoordGrid): Scan? {
        if (buildArea == CoordGrid.NULL) {
            return null
        }
        // Instances can change content at identical coordinates and cannot reuse scene membership.
        if (RegionRegistry.inWorkingArea(buildArea)) {
            return null
        }
        return cache.getOrPut(buildArea.packed) { build(buildArea) }
    }

    private fun build(buildArea: CoordGrid): Scan {
        val base = ZoneKey.from(buildArea)
        val span = BuildAreaUtils.SIZE / ZoneGrid.LENGTH
        val foundPatches = LinkedHashMap<FarmingPatchKey, FarmingPatchDef>()
        val foundBins = LinkedHashMap<FarmingBinKey, FarmingBinDef>()
        for (level in 0 until LEVEL_COUNT) {
            for (dx in 0 until span) {
                for (dz in 0 until span) {
                    val zone = ZoneKey(base.x + dx, base.z + dz, level)
                    for (loc in locReg.findAll(zone)) {
                        val patch = patches.byLoc(loc.id)
                        if (patch != null) {
                            foundPatches.putIfAbsent(patch.key, patch)
                            continue
                        }
                        val bin = bins.byLoc(loc.id) ?: continue
                        foundBins.putIfAbsent(bin.key, bin)
                    }
                }
            }
        }
        val scan =
            Scan(
                patches = if (foundPatches.isEmpty()) emptyList() else foundPatches.values.toList(),
                bins = if (foundBins.isEmpty()) emptyList() else foundBins.values.toList(),
            )
        warnOnSlotClash(buildArea, scan)
        return scan
    }

    /**
     * Two visible patches/bins cannot share a transmit slot without overwriting each other's
     * rendering.
     */
    private fun warnOnSlotClash(buildArea: CoordGrid, scan: Scan) {
        val owners = HashMap<FarmingTransmitSlot, String>()
        for (def in scan.patches) {
            val slot = def.transmitSlot ?: continue
            val clash = owners.put(slot, def.key.id)
            if (clash != null) {
                logger.warn { "Farming slot $slot claimed by $clash and ${def.key} at $buildArea." }
            }
        }
        for (def in scan.bins) {
            val slot = def.transmitSlot ?: continue
            val clash = owners.put(slot, def.key.id)
            if (clash != null) {
                logger.warn { "Farming slot $slot claimed by $clash and ${def.key} at $buildArea." }
            }
        }
    }

    private class Scan(val patches: List<FarmingPatchDef>, val bins: List<FarmingBinDef>)

    private companion object {
        private const val LEVEL_COUNT: Int = 4
    }
}
