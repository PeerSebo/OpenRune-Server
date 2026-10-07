package org.rsmod.api.poh

import org.rsmod.map.CoordGrid

public data class PohRoomSlot(val level: Int, val gridX: Int, val gridZ: Int) {
    public fun translate(edge: PohDoorEdge): PohRoomSlot =
        PohRoomSlot(level, gridX + edge.deltaX, gridZ + edge.deltaZ)

    public fun persistenceKey(): String = "${level}_${gridX}_$gridZ"

    public companion object {
        public fun fromPersistenceKey(key: String): PohRoomSlot? {
            val parts = key.split('_')
            if (parts.size != 3) return null
            val (level, gridX, gridZ) = parts.map { it.toIntOrNull() ?: return null }
            return PohRoomSlot(level, gridX, gridZ)
        }
    }
}

public data class PohRoom(val type: String, val rotation: Int)

public data class PohHotspotSlot(
    val level: Int,
    val gridX: Int,
    val gridZ: Int,
    val hotspotIndex: Int,
) {
    public val roomSlot: PohRoomSlot
        get() = PohRoomSlot(level, gridX, gridZ)

    public fun persistenceKey(): String = "${level}_${gridX}_${gridZ}_$hotspotIndex"

    public companion object {
        public fun fromPersistenceKey(key: String): PohHotspotSlot? {
            val parts = key.split('_')
            if (parts.size != 4) return null
            val (level, gridX, gridZ, index) = parts.map { it.toIntOrNull() ?: return null }
            return PohHotspotSlot(level, gridX, gridZ, index)
        }
    }
}

public class PohHouse(
    public var size: Int,
    public var style: Int,
    public var location: Int,
    public val rooms: MutableMap<PohRoomSlot, PohRoom> = mutableMapOf(),
    public val furniture: MutableMap<PohHotspotSlot, String> = mutableMapOf(),
) {
    public fun inBuildableArea(slot: PohRoomSlot): Boolean {
        val start = (PohConstants.GRID_LENGTH - size) / 2
        val end = start + size - 1
        return slot.gridX in start..end && slot.gridZ in start..end
    }

    public fun furnitureAt(slot: PohRoomSlot): Map<PohHotspotSlot, String> =
        furniture.filterKeys { it.roomSlot == slot }
}

/**
 * [varValue] is stored in `varbit.poh_house_location`; 0 means no house.
 * [levelReq] uses base Construction, excluding boosts. Only Prifddinas and Aldarin exceed level 1.
 */
public enum class PohLocation(
    public val varValue: Int,
    public val displayName: String,
    public val exitCoords: CoordGrid,
    public val relocateCost: Int,
    public val levelReq: Int = 1,
) {
    RIMMINGTON(1, "Rimmington", CoordGrid(2953, 3224, 0), 5_000),
    TAVERLEY(2, "Taverley", CoordGrid(2893, 3465, 0), 5_000),
    POLLNIVNEACH(3, "Pollnivneach", CoordGrid(3340, 3003, 0), 7_500),
    RELLEKKA(4, "Rellekka", CoordGrid(2670, 3631, 0), 10_000),
    BRIMHAVEN(5, "Brimhaven", CoordGrid(2757, 3178, 0), 15_000),
    YANILLE(6, "Yanille", CoordGrid(2544, 3095, 0), 25_000),
    KOUREND(7, "Hosidius", CoordGrid(1742, 3517, 0), 8_750),

    // Prifddinas relocation: https://oldschool.runescape.wiki/w/Estate_agent
    PRIFDDINAS(8, "Prifddinas", CoordGrid(3239, 6079, 0), 50_000, levelReq = 70),

    // Aldarin relocation: https://oldschool.runescape.wiki/w/Estate_agent
    ALDARIN(9, "Aldarin", CoordGrid(1422, 2964, 0), 12_500, levelReq = 35);

    public companion object {
        public fun forVarValue(value: Int): PohLocation? =
            entries.firstOrNull { it.varValue == value }
    }
}

public object PohConstants {
    public const val GRID_LENGTH: Int = 7

    public const val ZONE_TILE_LENGTH: Int = 8

    /**
     * Landscape beyond the 7x7 rooms because the client rebuilds a 13x13-zone view around the
     * player.
     */
    public const val BUILD_AREA_ZONE_RADIUS: Int = 6

    public const val GRID_ZONE_OFFSET: Int = 16

    public const val LANDSCAPE_MIN_ZONE: Int = GRID_ZONE_OFFSET - 1 - BUILD_AREA_ZONE_RADIUS
    public const val LANDSCAPE_MAX_ZONE: Int =
        GRID_ZONE_OFFSET + GRID_LENGTH + BUILD_AREA_ZONE_RADIUS

    public const val WALKABLE_MIN_TILE: Int = (GRID_ZONE_OFFSET - 1) * ZONE_TILE_LENGTH
    public const val WALKABLE_MAX_TILE: Int =
        (GRID_ZONE_OFFSET + GRID_LENGTH + 1) * ZONE_TILE_LENGTH - 1

    public const val HOUSE_COST: Int = 1_000

    public const val EXIT_PORTAL_LOC: String = "loc.poh_exit_portal"

    public const val GARDEN_CENTREPIECE_LOC: String = "loc.poh_crude_garden_1"

    public fun gridSize(constructionLevel: Int): Int =
        when {
            constructionLevel >= 60 -> 7
            constructionLevel >= 45 -> 6
            constructionLevel >= 30 -> 5
            constructionLevel >= 15 -> 4
            else -> 3
        }

    private val ROOM_COUNT_GATES =
        intArrayOf(26, 32, 38, 44, 50, 56, 62, 68, 74, 80, 86, 92, 96, 99)

    public fun maxRooms(constructionLevel: Int): Int =
        24 + ROOM_COUNT_GATES.count { constructionLevel >= it }
}
