package org.rsmod.content.minigames.pyramidplunder.configs

import org.rsmod.map.CoordGrid
data class PlunderRoom(
    val room: Int,
    val levelReq: Int,
    val urnXp: Double,
    val chestXp: Double,
    val doorXp: Double?,
    val sarcophagusXp: Double,
    val sarcophagusOpenLow: Int,
    val sarcophagusOpenHigh: Int,
    val mummySpawnChance: Int,
    val sceptreDenominator: Int,
    val landing: CoordGrid,
    val trapBand: PlunderLocs.TrapBand,
)

object PlunderRooms {
    const val TRAP_XP: Double = 10.0
    const val TRAP_LOW: Int = 160
    const val TRAP_HIGH: Int = 250

    const val URN_SEARCH_LOW: Int = 100
    const val URN_SEARCH_HIGH: Int = 180
    const val URN_CHECK_LOW: Int = 160
    const val URN_CHECK_HIGH: Int = 250
    const val URN_SEARCH_WITH_SNAKE_LOW: Int = 130
    const val URN_SEARCH_WITH_SNAKE_HIGH: Int = 220

    const val CHEST_LOW: Int = 130
    const val CHEST_HIGH: Int = 220

    const val DOOR_LOW: Int = 130
    const val DOOR_HIGH: Int = 220
    const val DOOR_LOCKPICK_LOW: Int = 160
    const val DOOR_LOCKPICK_HIGH: Int = 250

    const val OUTER_DOOR_LOW: Int = 160
    const val OUTER_DOOR_HIGH: Int = 250

    const val OUTER_DOOR_XP: Double = 20.0
    const val OUTER_DOOR_LEVEL_REQ: Int = 21

    val ALL: List<PlunderRoom> get() = PlunderRoomData.rooms

    fun forRoom(room: Int): PlunderRoom =
        ALL.getOrNull(room - 1) ?: error("Invalid Pyramid Plunder room number: $room")
}
