package org.rsmod.content.other.commands

import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.StaffVisibilityChanged
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class StaffInvisibilityCommandTest {
    @Test fun `ordinary accounts cannot enable staff invisibility`() {
        val f = Fixture()
        assertTrue(f.commands.execute(f.player, "invis", emptyList()))
        assertFalse(f.player.staffInvisible)
        assertEquals(0, f.changes)
    }

    @Test fun `admin toggle clears facing references and movement and restores visibility and collision`() {
        val f = Fixture()
        f.player.modLevel = Rights.ADMINISTRATOR
        f.player.walk(f.player.coords.translate(2, 0))
        f.observer.facePlayer(f.player)
        assertEquals(EntityFaceTarget(f.player), f.observer.faceEntity)
        f.execute()
        assertTrue(f.player.staffInvisible)
        assertTrue(f.player.isInvisible)
        assertEquals(0, f.collision[f.player.x, f.player.z, f.player.level] and CollisionFlag.BLOCK_NPCS)
        assertEquals(EntityFaceTarget.NULL, f.observer.faceEntity)
        assertTrue(f.player.routeDestination.isEmpty())
        assertNull(f.player.routeRequest)
        f.observer.facePlayer(f.player)
        assertEquals(EntityFaceTarget.NULL, f.observer.faceEntity, "Future facing must not disclose a hidden player slot")
        f.execute()
        assertFalse(f.player.staffInvisible)
        assertTrue(f.player.isVisible)
        assertNotEquals(0, f.collision[f.player.x, f.player.z, f.player.level] and CollisionFlag.BLOCK_NPCS)
        assertEquals(2, f.changes)
        assertFalse(Player().staffInvisible)
    }

    @Test fun `unexpected arguments do not toggle or publish presence changes`() {
        val f = Fixture()
        f.player.modLevel = Rights.ADMINISTRATOR
        assertTrue(f.commands.execute(f.player, "invis", listOf("someone")))
        assertFalse(f.player.staffInvisible)
        assertEquals(0, f.changes)
    }

    private class Fixture {
        val events = EventBus()
        val commands = CheatCommandMap()
        val collision = CollisionFlagMap()
        val player = Player().apply { slotId = 1; coords = CoordGrid(3200, 3200) }
        val observer = Player().apply { slotId = 2; coords = CoordGrid(3201, 3200) }
        var changes = 0

        init {
            val players = PlayerList().apply { this[1] = player; this[2] = observer }
            collision.allocateIfAbsent(player.x, player.z, player.level)
            player.addBlockWalkCollision(collision, player.coords)
            events.subscribeUnbound(StaffVisibilityChanged::class.java) { changes++ }
            with(StaffInvisibilityCommands(collision, players, NpcList(), events)) {
                ScriptContext(events, commands, EngineQueueCache()).startup()
            }
        }
        fun execute() { assertTrue(commands.execute(player, "invis", emptyList())) }
    }
}
