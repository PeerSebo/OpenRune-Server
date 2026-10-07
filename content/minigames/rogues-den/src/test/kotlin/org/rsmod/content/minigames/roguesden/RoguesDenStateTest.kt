package org.rsmod.content.minigames.roguesden

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.disarmTrap
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.disarmedTrap
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.grantedMazeAccess
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.content.minigames.roguesden.scripts.RoguesDenStateScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class RoguesDenStateTest {
    @Test
    fun `legacy access imports without persisting active maze or disarmed traps`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(Legacy to true, "unrelated" to 9))
        assertTrue(player.grantedMazeAccess)
        player.inRoguesDenMaze = true
        player.disarmTrap(Trap)
        val restored = Player()
        for ((id, value) in player.vars) {
            if (ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm) restored.vars.backing[id] = value
        }
        restored.attr.putAllFromPersistence(player.attr.toPersistentMap())
        assertTrue(restored.grantedMazeAccess)
        assertFalse(restored.inRoguesDenMaze)
        assertFalse(restored.disarmedTrap(Trap))
        assertEquals(mapOf("unrelated" to 9), restored.attr.toPersistentMap())
    }

    @Test
    fun `explicit denied access and a later reset cannot import stale permission`() {
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varp.rogues_den_maze_access", 0)
        player.attr.putAllFromPersistence(mapOf(Legacy to true))
        assertFalse(player.grantedMazeAccess)
        player.grantedMazeAccess = true
        player.grantedMazeAccess = false
        player.attr.putAllFromPersistence(mapOf(Legacy to true))
        assertFalse(player.grantedMazeAccess)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `bound death cleanup clears maze and trap state but retains permanent access`() {
        val player = Player()
        player.grantedMazeAccess = true
        player.inRoguesDenMaze = true
        player.disarmTrap(Trap)
        val hooks = Guice.createInjector(RoguesDenModule())
            .getInstance(Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {}))
        assertEquals(1, hooks.size)
        hooks.single().cleanup(player)
        assertFalse(player.inRoguesDenMaze)
        assertFalse(player.disarmedTrap(Trap))
        assertTrue(player.grantedMazeAccess)
    }

    @Test
    fun `session hooks clear maze and trap state without clearing access`() {
        val player = Player().apply {
            inv = Inventory.create("inv.inv")
            coords = CoordGrid(3049, 4976, 1)
        }
        val events = EventBus()
        val collision = CollisionFlagMap().apply { allocateIfAbsent(3049, 4976, 1) }
        with(RoguesDenStateScript(collision)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        player.grantedMazeAccess = true
        for (event in listOf(SessionStateEvent.Logout(player), SessionStateEvent.Login(player))) {
            player.inRoguesDenMaze = true
            player.disarmTrap(Trap)
            events.publish(event)
            assertFalse(player.inRoguesDenMaze)
            assertFalse(player.disarmedTrap(Trap))
            assertTrue(player.grantedMazeAccess)
        }
    }

    companion object {
        private const val Legacy = "roguesden_spoken_to_brian"
        private val Trap = CoordGrid(3048, 4992, 1)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
