package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.poh.PohConstants
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohDoorEdge
import org.rsmod.api.poh.PohFloor
import org.rsmod.api.poh.PohHouse
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.PohRoom
import org.rsmod.api.poh.PohRoomSlot
import org.rsmod.api.poh.pohBuildingMode
import org.rsmod.api.script.onOpLoc5
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RoomScript
@Inject
constructor(private val manager: PohManager, private val dataStore: PohDataStore) : PluginScript() {
    override fun ScriptContext.startup() {
        val doorLocs =
            dataStore.styles.flatMap { listOf(it.doorLeftLoc, it.doorRightLoc) }.distinct()
        for (doorLoc in doorLocs) {
            onOpLoc5(doorLoc) { onDoorBuild(it.loc.coords) }
        }
    }

    private suspend fun ProtectedAccess.onDoorBuild(coords: org.rsmod.map.CoordGrid) {
        if (player.pohBuildingMode != 1) {
            mes("You can only do that in building mode.")
            return
        }
        val house = manager.houseOf(player) ?: return
        val (slot, edge) = manager.findDoorEdge(player, coords) ?: return
        val target = slot.translate(edge)
        val existing = house.rooms[target]
        if (existing != null) {
            val region = manager.activeHouse(player)?.region
            val playerSlot = region?.let { manager.roomSlotAt(it, player.coords) }
            removeRoom(house, if (playerSlot == target) slot else target)
        } else {
            addRoom(house, target, edge)
        }
    }

    private suspend fun ProtectedAccess.addRoom(
        house: PohHouse,
        target: PohRoomSlot,
        edge: PohDoorEdge,
    ) {
        if (!house.inBuildableArea(target)) {
            mes("Your house isn't big enough for that.")
            return
        }
        if (house.rooms.size >= PohConstants.maxRooms(player.baseConstructionLvl)) {
            mes("You can't build any more rooms at your Construction level.")
            return
        }
        if (target.level == PohFloor.UPPER.destLevel) {
            val below = PohRoomSlot(PohFloor.GROUND.destLevel, target.gridX, target.gridZ)
            val support = house.rooms[below]
            if (support == null || support.type in OPEN_AIR_ROOMS) {
                mes("You can't build a room up there with nothing to support it.")
                return
            }
        }

        val options = ROOM_OPTIONS.filter { target.level in it.floors }
        if (options.isEmpty()) {
            mes("You can't build a room there.")
            return
        }
        val labels = options.map { "${it.label} (${it.cost} coins) - level ${it.level}" }
        val index = menu("Select a room", hotkeys = false, labels)
        val option = options.getOrNull(index) ?: return

        if (player.baseConstructionLvl < option.level) {
            mes("You need a Construction level of ${option.level} to build that room.")
            return
        }
        if (invTotal(inv, COINS) < option.cost) {
            mes("You need ${option.cost} coins to build that room.")
            return
        }

        val roomKey = option.roomKeyFor(target.level)
        if (roomKey == COSTUME_ROOM && house.rooms.values.any { it.type == COSTUME_ROOM }) {
            mes("Your house can only have one costume room.")
            return
        }
        if (roomKey in MENAGERIE_ROOMS && house.rooms.values.any { it.type in MENAGERIE_ROOMS }) {
            mes("Your house can only have one menagerie.")
            return
        }
        val rotation = alignRotation(roomKey, edge)
        if (rotation == null) {
            mes("The room's doors don't line up.")
            return
        }

        val confirm =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Build ${option.label} for ${option.cost} coins?",
            )
        if (!confirm) {
            return
        }

        invDel(inv, COINS, option.cost)
        manager.addRoom(player, target, PohRoom(roomKey, rotation))
        mes("You build a ${option.label.lowercase()}.")
    }

    private suspend fun ProtectedAccess.removeRoom(house: PohHouse, target: PohRoomSlot) {
        if (target.level == PohFloor.GROUND.destLevel) {
            val above = PohRoomSlot(PohFloor.UPPER.destLevel, target.gridX, target.gridZ)
            if (house.rooms.containsKey(above)) {
                mes("You can't remove a room that has a room above it.")
                return
            }
        }
        val room = house.rooms[target]
        if (room?.type == COSTUME_ROOM && house.furnitureAt(target).isNotEmpty()) {
            mes("You must empty the costume room before you can remove it.")
            return
        }
        // Keep an exit portal or a garden where the fallback portal can be placed.
        val exitElsewhere =
            house.furniture.any { (slot, loc) ->
                loc == PohConstants.EXIT_PORTAL_LOC && slot.roomSlot != target
            }
        val gardenElsewhere =
            house.rooms.any { (slot, room) -> slot != target && room.type in EXIT_GARDEN_ROOMS }
        if (!exitElsewhere && !gardenElsewhere) {
            mes("Your house must have at least one exit portal.")
            return
        }
        val confirm = choice2("Yes.", true, "No.", false, title = "Really remove it?")
        if (!confirm) {
            return
        }
        manager.removeRoom(player, target)
        mes("You remove the room.")
    }

    private fun alignRotation(roomKey: String, edge: PohDoorEdge): Int? {
        val type = dataStore.room(roomKey)
        if (type.doorEdges.isEmpty()) {
            return null
        }
        for (rotation in 0..3) {
            if (type.doorEdges.any { it.rotate(rotation) == edge.opposite }) {
                return rotation
            }
        }
        return null
    }

    private data class RoomOption(
        val label: String,
        val roomKey: String,
        val level: Int,
        val cost: Int,
        val floors: Set<Int>,
        val upperRoomKey: String = roomKey,
    ) {
        fun roomKeyFor(destLevel: Int): String =
            if (destLevel == PohFloor.UPPER.destLevel) upperRoomKey else roomKey
    }

    private companion object {

        val EXIT_GARDEN_ROOMS = setOf("garden", "superior_garden")
        const val COINS = "obj.coins"

        const val COSTUME_ROOM = "costume_room"
        val MENAGERIE_ROOMS = setOf("menagerie_indoor", "menagerie_outdoor")

        val OPEN_AIR_ROOMS =
            setOf("garden", "formal_garden", "superior_garden", "menagerie_outdoor")

        val GROUND_FLOORS = setOf(PohFloor.GROUND.destLevel, PohFloor.UPPER.destLevel)
        val GROUND_ONLY = setOf(PohFloor.GROUND.destLevel)
        val DUNGEON_ONLY = setOf(PohFloor.DUNGEON.destLevel)

        val ROOM_OPTIONS =
            listOf(
                RoomOption("Parlour", "parlour", 1, 1_000, GROUND_FLOORS),
                RoomOption("Garden", "garden", 1, 1_000, GROUND_ONLY),
                RoomOption("Kitchen", "kitchen", 5, 5_000, GROUND_FLOORS),
                RoomOption("Dining room", "dining_room", 10, 5_000, GROUND_FLOORS),
                RoomOption("Workshop", "workshop", 15, 10_000, GROUND_FLOORS),
                RoomOption("Bedroom", "bedroom", 20, 10_000, GROUND_FLOORS),
                RoomOption(
                    "Skill hall",
                    "skill_hall_stairs_up",
                    25,
                    15_000,
                    GROUND_FLOORS,
                    upperRoomKey = "skill_hall_stairs_top",
                ),
                RoomOption("Games room", "games_room", 30, 25_000, GROUND_FLOORS),
                RoomOption("Combat room", "combat_room", 32, 25_000, GROUND_FLOORS),
                RoomOption(
                    "Quest hall",
                    "quest_hall_stairs_up",
                    35,
                    25_000,
                    GROUND_FLOORS,
                    upperRoomKey = "quest_hall_stairs_top",
                ),
                RoomOption("Menagerie", "menagerie_outdoor", 37, 30_000, GROUND_ONLY),
                RoomOption("Menagerie (indoors)", "menagerie_indoor", 37, 30_000, GROUND_ONLY),
                RoomOption("Study", "study", 40, 50_000, GROUND_FLOORS),
                RoomOption("Costume room", "costume_room", 42, 50_000, GROUND_FLOORS),
                RoomOption("Chapel", "chapel", 45, 50_000, GROUND_FLOORS),
                RoomOption("Portal chamber", "portal_chamber", 50, 100_000, GROUND_FLOORS),
                RoomOption("Formal garden", "formal_garden", 55, 75_000, GROUND_ONLY),
                RoomOption("Throne room", "throne_room", 60, 150_000, GROUND_ONLY),
                RoomOption("Superior garden", "superior_garden", 65, 75_000, GROUND_ONLY),
                RoomOption("Oubliette", "oubliette", 65, 150_000, DUNGEON_ONLY),
                RoomOption("Dungeon corridor", "dungeon_corridor", 70, 7_500, DUNGEON_ONLY),
                RoomOption("Dungeon junction", "dungeon_junction", 70, 7_500, DUNGEON_ONLY),
                RoomOption("Dungeon stairs", "dungeon_stairs", 70, 7_500, DUNGEON_ONLY),
                RoomOption("Portal nexus", "portal_nexus", 72, 200_000, GROUND_FLOORS),
                RoomOption("Treasure room", "treasure_room", 75, 250_000, DUNGEON_ONLY),
                RoomOption("Achievement gallery", "achievement_gallery", 80, 200_000, GROUND_FLOORS),
                RoomOption("League hall", "league_hall", 27, 15_000, GROUND_ONLY),
            )
    }
}
