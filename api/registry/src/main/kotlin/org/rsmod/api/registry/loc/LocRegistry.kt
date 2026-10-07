package org.rsmod.api.registry.loc

import jakarta.inject.Inject
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

public class LocRegistry
@Inject
constructor(
    private val locZones: LocZoneStorage,
    private val normalReg: LocRegistryNormal,
    private val regionReg: LocRegistryRegion,
) {
    public fun add(loc: LocInfo): LocRegistryResult.Add =
        if (inRegionArea(loc.coords)) {
            regionReg.add(loc)
        } else {
            normalReg.add(loc)
        }

    public fun del(loc: LocInfo): LocRegistryResult.Delete =
        if (inRegionArea(loc.coords)) {
            regionReg.del(loc)
        } else {
            normalReg.del(loc)
        }

    public fun findType(coords: CoordGrid, loc: Int): LocInfo? =
        if (inRegionArea(coords)) {
            regionReg.findType(coords, loc)
        } else {
            normalReg.findType(coords, loc)
        }

    public fun findShape(coords: CoordGrid, shape: Int): LocInfo? =
        if (inRegionArea(coords)) {
            regionReg.findShape(coords, shape)
        } else {
            normalReg.findShape(coords, shape)
        }

    public fun findAll(zone: ZoneKey): Sequence<LocInfo> =
        if (inRegionArea(zone)) {
            regionReg.findAll(zone)
        } else {
            normalReg.findAll(zone)
        }

    /**
     * Includes only dynamic locs; use findAll for the effective map with static locs and
     * replacements.
     */
    public fun findAllSpawned(zone: ZoneKey): Sequence<LocInfo> {
        val entries = locZones.spawnedLocs[zone]?.byte2IntEntrySet() ?: return emptySequence()
        return sequence {
            val zoneCoords = zone.toCoords()
            for (entry in entries) {
                val entity = LocEntity(entry.intValue)
                val key = LocZoneKey(entry.byteKey)
                val coords = zoneCoords.translate(key.x, key.z)
                val loc = LocInfo(key.layer, coords, entity)
                yield(loc)
            }
        }
    }

    public fun isValid(coords: CoordGrid, loc: Int): Boolean {
        val found = findType(coords, loc) ?: return false
        return found.entity.id != DELETED_LOC_ID
    }

    public companion object {
        public val DELETED_LOC_ID: Int = LocEntity.NULL.id

        private fun inRegionArea(zone: ZoneKey): Boolean = RegionRegistry.inWorkingArea(zone)

        private fun inRegionArea(coords: CoordGrid): Boolean = RegionRegistry.inWorkingArea(coords)
    }
}
