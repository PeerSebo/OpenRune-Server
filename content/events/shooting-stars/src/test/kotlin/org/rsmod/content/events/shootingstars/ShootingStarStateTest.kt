package org.rsmod.content.events.shootingstars

import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.events.shootingstars.scripts.ShootingStarStateScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class ShootingStarStateTest {
    @Test
    fun `legacy discovery bonus total dust and seen flag import independently on login`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(
            LegacyBonus to 17, LegacyDust to 3456, LegacySeen to true, "unrelated" to 4,
        ))
        events().publish(SessionStateEvent.Login(player))
        assertEquals(17, player.discovererBonusRemaining)
        assertEquals(3456, player.allTimeStarDust)
        assertTrue(player.seenShootingStar)
        assertEquals(mapOf("unrelated" to 4), player.attr.toPersistentMap())
    }

    @Test
    fun `explicit zeros take precedence while missing companion values still import`() {
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varp.shooting_star_discoverer_bonus", 0)
        VarPlayerIntMapSetter.set(player, "varp.shooting_star_seen", 0)
        player.attr.putAllFromPersistence(mapOf(LegacyBonus to 50, LegacyDust to 300, LegacySeen to true))
        assertEquals(0, player.discovererBonusRemaining)
        assertEquals(300, player.allTimeStarDust)
        assertFalse(player.seenShootingStar)
    }

    @Test
    fun `spending the last bonus and updating dust survives reload without resurrecting old values`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(LegacyBonus to 1, LegacyDust to 20))
        player.discovererBonusRemaining--
        player.allTimeStarDust += 3
        player.seenShootingStar = true
        val restored = reload(player)
        restored.attr.putAllFromPersistence(mapOf(LegacyBonus to 50, LegacyDust to 999))
        events().publish(SessionStateEvent.Login(restored))
        assertEquals(0, restored.discovererBonusRemaining)
        assertEquals(23, restored.allTimeStarDust)
        assertTrue(restored.seenShootingStar)
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `mining flag clears at session boundaries and is excluded from saved state`() {
        val player = Player()
        val events = events()
        player.miningShootingStar = true
        assertFalse(reload(player).miningShootingStar)
        events.publish(SessionStateEvent.Logout(player))
        assertFalse(player.miningShootingStar)
        player.miningShootingStar = true
        events.publish(SessionStateEvent.Login(player))
        assertFalse(player.miningShootingStar)
    }

    private fun events() = EventBus().also { events ->
        with(ShootingStarStateScript()) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    private fun reload(player: Player): Player = Player().also { restored ->
        for ((id, value) in player.vars) {
            if (ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm) restored.vars.backing[id] = value
        }
        restored.attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    companion object {
        private const val LegacyBonus = "shooting_star_discoverer_bonus"
        private const val LegacyDust = "shooting_star_all_time_dust"
        private const val LegacySeen = "shooting_star_seen"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
