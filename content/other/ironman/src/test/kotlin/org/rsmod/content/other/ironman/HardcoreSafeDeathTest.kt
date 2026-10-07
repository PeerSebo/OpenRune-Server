package org.rsmod.content.other.ironman

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.hasSafeDeathMark
import org.rsmod.api.player.ironman.markNextDeathSafe
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class HardcoreSafeDeathTest {
    @Test fun `safe death preserves hardcore status exactly once`() {
        val player = Player().apply { setGamemode(PlayerGamemode.HARDCORE_IRONMAN) }
        val hook = HardcoreIronmanDeathHook()
        player.markNextDeathSafe()
        hook.cleanup(player)
        assertEquals(PlayerGamemode.HARDCORE_IRONMAN, player.gamemode)
        assertFalse(player.hasSafeDeathMark())

        hook.cleanup(player)
        assertEquals(PlayerGamemode.IRONMAN, player.gamemode)
    }

    @Test fun `ordinary accounts consume safe marker without changing their mode`() {
        for (mode in listOf(PlayerGamemode.NORMAL, PlayerGamemode.IRONMAN, PlayerGamemode.ULTIMATE_IRONMAN)) {
            val player = Player().apply { setGamemode(mode) }
            player.markNextDeathSafe()
            HardcoreIronmanDeathHook().cleanup(player)
            assertEquals(mode, player.gamemode)
            assertFalse(player.hasSafeDeathMark())
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
