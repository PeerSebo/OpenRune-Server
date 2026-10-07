package org.rsmod.api.player.hit

import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class PlayerAbsorptionTest {
    @Test fun `damage uses absorption before hitpoints and cannot consume more than current hitpoints`() {
        val player = Player().apply { statMap.setCurrentLevel("stat.hitpoints", 20) }
        PlayerAbsorption.add(player, 25, 1000)
        assertEquals(0, PlayerAbsorption.absorb(player, 100))
        assertEquals(5, PlayerAbsorption.points(player))
        assertEquals(15, PlayerAbsorption.absorb(player, 100))
        assertEquals(0, PlayerAbsorption.points(player))
        assertEquals(20, PlayerAbsorption.absorb(player, 100))
    }

    @Test fun `point increases cap without integer overflow and invalid input preserves state`() {
        val player = Player()
        assertEquals(1000, PlayerAbsorption.add(player, 2000, 1000))
        assertEquals(1000, PlayerAbsorption.add(player, Int.MAX_VALUE, 1000))
        assertThrows(IllegalArgumentException::class.java) { PlayerAbsorption.add(player, -1, 1000) }
        assertThrows(IllegalArgumentException::class.java) { PlayerAbsorption.add(player, 1, -1) }
        assertEquals(1000, PlayerAbsorption.points(player))
    }

    @Test fun `non-damaging hits preserve points and explicit clearing removes protection`() {
        val player = Player().apply { statMap.setCurrentLevel("stat.hitpoints", 10) }
        PlayerAbsorption.add(player, 50, 1000)
        assertEquals(0, PlayerAbsorption.absorb(player, 0))
        assertEquals(0, PlayerAbsorption.absorb(player, -10))
        player.statMap.setCurrentLevel("stat.hitpoints", 0)
        assertEquals(0, PlayerAbsorption.absorb(player, 10))
        assertEquals(50, PlayerAbsorption.points(player))
        PlayerAbsorption.clear(player)
        PlayerAbsorption.clear(player)
        assertEquals(0, PlayerAbsorption.points(player))
    }

    @Test fun `absorption does not survive a permanent-only account reload`() {
        val player = Player()
        PlayerAbsorption.add(player, 100, 1000)
        val restored = Player().apply {
            vars.backing.putAll(player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertEquals(0, PlayerAbsorption.points(restored))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
