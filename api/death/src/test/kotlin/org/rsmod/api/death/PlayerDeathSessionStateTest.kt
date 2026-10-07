package org.rsmod.api.death

import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.player.death.DEATH_CAUSE_ATTR
import org.rsmod.api.player.death.DeathCause
import org.rsmod.api.player.hit.PlayerAbsorption
import org.rsmod.api.player.ironman.hasSafeDeathMark
import org.rsmod.api.player.ironman.markNextDeathSafe
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class PlayerDeathSessionStateTest {
    @Test fun `absent PvP history differs from a hit at tick zero and preserves inclusive expiry`() {
        val player = Player().apply { currentMapClock = 0 }
        assertFalse(player.wasRecentlyHitByPlayer(0))
        player.recordPvpHit()
        assertTrue(player.wasRecentlyHitByPlayer(0))
        assertTrue(player.wasRecentlyHitByPlayer(RECENT_PVP_HIT_TICKS))
        assertFalse(player.wasRecentlyHitByPlayer(RECENT_PVP_HIT_TICKS + 1))
    }

    @Test fun `PvP death preparation records both the attacker and the current hit cycle`() {
        val player = Player().apply { currentMapClock = 123 }
        val killer = Player()
        player.preparePvpDeath(killer)
        assertTrue(player.wasRecentlyHitByPlayer(123))
        assertEquals(DeathCause.ByPlayer(killer), player.attr[DEATH_CAUSE_ATTR])
    }

    @Test fun `admin death-drop bypass is consumed exactly once`() {
        val player = Player()
        assertFalse(player.consumeAdminDeathDropBypass())
        player.prepareAdminDieTest()
        assertTrue(player.consumeAdminDeathDropBypass())
        assertFalse(player.consumeAdminDeathDropBypass())
        player.prepareAdminDieTest()
        assertTrue(player.consumeAdminDeathDropBypass())
    }

    @Test fun `death clears combat effects before hooks without consuming safe-death policy`() {
        val player = Player().apply { currentMapClock = 100 }
        Stun.apply(player, 5, 9)
        PlayerAbsorption.add(player, 100, 1000)
        player.markNextDeathSafe()
        player.resetDeathCombatEffects()
        assertFalse(Stun.isStunned(player))
        assertEquals(0, PlayerAbsorption.points(player))
        assertTrue(player.hasSafeDeathMark())
        player.resetDeathCombatEffects()
        assertTrue(player.hasSafeDeathMark())
    }

    @Test fun `combat and death session flags are excluded from persistent reloads`() {
        val player = Player().apply { currentMapClock = 100 }
        player.recordPvpHit()
        player.prepareAdminDieTest()
        player.markNextDeathSafe()
        Stun.apply(player, 10)
        PlayerAbsorption.add(player, 100, 1000)
        val restored = Player().apply {
            currentMapClock = 100
            vars.backing.putAll(player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertFalse(restored.wasRecentlyHitByPlayer(100))
        assertFalse(restored.consumeAdminDeathDropBypass())
        assertFalse(restored.hasSafeDeathMark())
        assertFalse(Stun.isStunned(restored))
        assertEquals(0, PlayerAbsorption.points(restored))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
