package org.rsmod.api.poh

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.zone.ZoneKey

/**
 * Turns a [PohHouse] into a [RegionStaticTemplate].
 *
 * Destination levels: 0 = dungeon, 1 = ground floor, 2 = upper floor, 3 = roofs (reserved and
 * unused - see [buildTemplate]). Each room's grid cell maps to region zone `(gridX +
 * GRID_ZONE_OFFSET, gridZ + GRID_ZONE_OFFSET)` - the grid and its lawn ring are centred inside the
 * 40x40 large-region slot, with the surrounding zones landscaped as visual padding.
 *
 * The per-zone setter is used deliberately: POH copies from the *style* source level into a *floor*
 * destination level, which `RegionStaticTemplate.copy()` cannot express.
 */
@Singleton
public class PohRegionBuilder @Inject constructor(private val dataStore: PohDataStore) {
    /**
     * Populate room zones and surrounding ground/dungeon filler; unallocated neighbouring zones
     * can leak old scene data.
     */
    public fun buildTemplate(house: PohHouse): RegionStaticTemplate {
        val style = dataStore.style(house.style)
        val grassZone = templateZone(style, dataStore.room("grass_filler"))
        val hiddenZone = hiddenFillZone(style)
        return RegionTemplate.createLarge {
            for ((slot, room) in house.rooms) {
                val type = dataStore.room(room.type)
                val srcZone = templateZone(style, type)
                val destX = slot.gridX + PohConstants.GRID_ZONE_OFFSET
                val destZ = slot.gridZ + PohConstants.GRID_ZONE_OFFSET
                when (room.rotation and 0x3) {
                    0 -> this[destX, destZ, slot.level] = srcZone
                    1 -> this[destX, destZ, slot.level] = srcZone.rotate90()
                    2 -> this[destX, destZ, slot.level] = srcZone.rotate180()
                    3 -> this[destX, destZ, slot.level] = srcZone.rotate270()
                }
            }
            // Fill unused ground zones with lawn and dungeon zones with darkness so the full
            // rebuilt area belongs to the house.
            for (zoneX in PohConstants.LANDSCAPE_MIN_ZONE..PohConstants.LANDSCAPE_MAX_ZONE) {
                for (zoneZ in PohConstants.LANDSCAPE_MIN_ZONE..PohConstants.LANDSCAPE_MAX_ZONE) {
                    val gridX = zoneX - PohConstants.GRID_ZONE_OFFSET
                    val gridZ = zoneZ - PohConstants.GRID_ZONE_OFFSET
                    val ground = PohRoomSlot(PohFloor.GROUND.destLevel, gridX, gridZ)
                    if (!house.rooms.containsKey(ground)) {
                        this[zoneX, zoneZ, PohFloor.GROUND.destLevel] = grassZone
                    }
                    val dungeon = PohRoomSlot(PohFloor.DUNGEON.destLevel, gridX, gridZ)
                    if (!house.rooms.containsKey(dungeon)) {
                        this[zoneX, zoneZ, PohFloor.DUNGEON.destLevel] = hiddenZone
                    }
                }
            }
        }
    }

    /**
     * An empty zone of the style's extra template square: explicit flat heights (the same 240 units
     * per level as every template zone) but no underlays, overlays, or locs - it contributes
     * stacking height while rendering as black void.
     */
    private fun hiddenFillZone(style: PohStyle): ZoneKey {
        val baseZoneX = (style.extraRegion shr 8) * PohConstants.ZONE_TILE_LENGTH
        val baseZoneZ = (style.extraRegion and 0xFF) * PohConstants.ZONE_TILE_LENGTH
        return ZoneKey(baseZoneX, baseZoneZ, style.level)
    }

    public fun templateZone(style: PohStyle, room: PohRoomType): ZoneKey {
        val region = if (room.extraSquare) style.extraRegion else style.region
        val baseZoneX = (region shr 8) * PohConstants.ZONE_TILE_LENGTH
        val baseZoneZ = (region and 0xFF) * PohConstants.ZONE_TILE_LENGTH
        return ZoneKey(baseZoneX + room.templateZoneX, baseZoneZ + room.templateZoneZ, style.level)
    }
}
