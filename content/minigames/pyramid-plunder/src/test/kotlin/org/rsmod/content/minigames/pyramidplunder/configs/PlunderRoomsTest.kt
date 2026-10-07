package org.rsmod.content.minigames.pyramidplunder.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.advancePlunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderChestState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderDoor1
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoomTier
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderThievingRequired
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderTrapActive
import org.rsmod.content.minigames.pyramidplunder.PlunderState.setUrnState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.urnState
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class PlunderRoomsTest {
    @Test
    fun `packed rooms preserve every original rate landing and trap band`() {
        assertEquals(original.size, PlunderRooms.ALL.size)
        for (values in original) {
            val room = PlunderRooms.forRoom(values[0].toInt())
            assertEquals(values, listOf(
                room.room, room.levelReq, room.urnXp, room.chestXp, room.doorXp,
                room.sarcophagusXp, room.sarcophagusOpenLow, room.sarcophagusOpenHigh,
                room.mummySpawnChance, room.sceptreDenominator, room.landing.x,
                room.landing.z, room.landing.level, room.trapBand.axis, room.trapBand.lo,
                room.trapBand.hi,
            ).map { it.toString() })
            assertEquals(room.landing, PlunderLocs.ROOM_LANDINGS[room.room])
            assertEquals(room.trapBand, PlunderLocs.TRAP_BANDS[room.room])
        }
        assertNull(PlunderRooms.forRoom(8).doorXp)
        assertEquals(-1, PlunderRooms.forRoom(7).sarcophagusOpenLow)
        assertEquals(-11, PlunderRooms.forRoom(8).sarcophagusOpenLow)
    }

    @Test
    fun `every room transition uses its original landing and requirement and resets containers`() {
        val player = Player()
        val collision = CollisionFlagMap()
        val events = EventBus()
        val context = ProtectedAccessContextFactory.empty().copy(
            getCollision = { collision },
            getEventBus = { events },
            getAreaChecker = { unused<AreaChecker>() },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
        )
        val access = ProtectedAccess(player, GameCoroutine(), context)
        for (values in original) {
            val room = values[0].toInt()
            val expected = CoordGrid(values[10].toInt(), values[11].toInt(), values[12].toInt())
            collision.allocateIfAbsent(expected.x, expected.z, expected.level)
            player.plunderRoom = room - 1
            player.plunderDoor1 = true
            player.plunderChestState = 1
            player.setUrnState(3, 3)
            access.advancePlunderRoom()
            assertEquals(expected, player.coords)
            assertEquals(room, player.plunderRoom)
            assertEquals(room + 1, player.plunderRoomTier)
            assertEquals(values[1].toInt(), player.plunderThievingRequired)
            assertTrue(player.plunderTrapActive)
            assertFalse(player.plunderDoor1)
            assertEquals(0, player.plunderChestState)
            assertEquals(0, player.urnState(3))
        }
    }

    @Test
    fun `trap crossing preserves both axes entry sides plane and already crossed behavior`() {
        for (values in original) {
            val room = values[0].toInt()
            val axisX = values[13] == "X"
            val lo = values[14].toInt()
            val hi = values[15].toInt()
            val landing = CoordGrid(values[10].toInt(), values[11].toInt(), 0)
            val entryHigh = (if (axisX) landing.x else landing.z) > hi
            for (position in (lo - 3)..(hi + 3)) {
                val from = if (axisX) CoordGrid(position, landing.z, 2) else CoordGrid(landing.x, position, 2)
                val needsCrossing = if (entryHigh) position >= lo else position <= hi
                val target = if (entryHigh) lo - 1 else hi + 1
                val expected = if (!needsCrossing) null
                else if (axisX) CoordGrid(target, from.z, 2) else CoordGrid(from.x, target, 2)
                assertEquals(expected, PlunderLocs.TRAP_BANDS.getValue(room).crossDestination(from, landing), "room $room from $from")
            }
        }
    }

    @Test
    fun `symbolic urn door and reward references retain their original IDs`() {
        val urns = listOf(26580, 26600, 26601, 26602, 26603, 26604, 26605, 26606, 26607, 26608, 26609, 26610, 26611, 26612, 26613)
        assertEquals(urns.withIndex().associate { it.value to it.index + 1 }, PlunderLocs.URN_PARENT_TO_INDEX)
        assertEquals(mapOf(26618 to 1, 26619 to 2, 26620 to 3, 26621 to 4), PlunderLocs.TOMB_DOOR_PARENT_TO_INDEX)
        assertEquals(5786, PlunderLocs.SIMON_TEMPLETON.asRSCM())
    }

    @Test
    fun `missing duplicate and malformed rooms fail validation without rejecting signed success rolls`() {
        val rooms = PlunderRooms.ALL
        assertEquals(rooms, PlunderRoomData.validate(rooms.reversed()))
        assertThrows(IllegalArgumentException::class.java) { PlunderRoomData.validate(rooms.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { PlunderRoomData.validate(rooms + rooms.first()) }
        assertThrows(IllegalArgumentException::class.java) { PlunderRoomData.validate(rooms.drop(1) + rooms.first().copy(sceptreDenominator = 0)) }
        assertThrows(IllegalStateException::class.java) { PlunderRooms.forRoom(0) }
        assertThrows(IllegalStateException::class.java) { PlunderRooms.forRoom(9) }
    }

    companion object {
        private val original = checkNotNull(PlunderRoomsTest::class.java.getResource("/plunder-rooms-before.tsv"))
            .readText().lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
