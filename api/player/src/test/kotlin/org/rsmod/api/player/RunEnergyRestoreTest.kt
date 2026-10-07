package org.rsmod.api.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.entity.Player

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class RunEnergyRestoreTest {
    @Test
    fun `fresh and cleared players are not blocked before their clock is initialized`() {
        val player = Player()
        assertFalse(player.isRunEnergyRestoreBlocked)
        player.clearRunEnergyRestoreBlock()
        assertFalse(player.isRunEnergyRestoreBlocked)
    }

    @Test
    fun `overlapping suppression extends its deadline without a hundred tick cap`() {
        val player = Player().apply { currentMapClock = 100 }
        assertFalse(player.isRunEnergyRestoreBlocked)
        player.blockRunEnergyRestore(150)
        player.currentMapClock = 101
        player.blockRunEnergyRestore(1)
        player.currentMapClock = 249
        assertTrue(player.isRunEnergyRestoreBlocked)
        player.currentMapClock = 250
        assertFalse(player.isRunEnergyRestoreBlocked)
        player.blockRunEnergyRestore(10)
        player.blockRunEnergyRestore(20)
        player.currentMapClock = 269
        assertTrue(player.isRunEnergyRestoreBlocked)
        player.currentMapClock = 270
        assertFalse(player.isRunEnergyRestoreBlocked)
    }

    @Test
    fun `zero duration and clearing leave recovery enabled and negative input changes nothing`() {
        val player = Player().apply { currentMapClock = 100 }
        player.blockRunEnergyRestore(0)
        assertFalse(player.isRunEnergyRestoreBlocked)
        player.blockRunEnergyRestore(10)
        assertThrows(IllegalArgumentException::class.java) { player.blockRunEnergyRestore(-1) }
        assertTrue(player.isRunEnergyRestoreBlocked)
        player.clearRunEnergyRestoreBlock()
        assertFalse(player.isRunEnergyRestoreBlocked)
    }

    @Test
    fun `protected action releases suppression after success and failure`() = runBlocking {
        val player = Player().apply { currentMapClock = 100 }
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        access.withoutRunEnergyRestore { assertTrue(player.isRunEnergyRestoreBlocked) }
        assertFalse(player.isRunEnergyRestoreBlocked)
        val failure = runCatching {
            access.withoutRunEnergyRestore {
                assertTrue(player.isRunEnergyRestoreBlocked)
                error("Interrupted obstacle")
            }
        }
        assertTrue(failure.isFailure)
        assertFalse(player.isRunEnergyRestoreBlocked)
    }

    @Test
    fun `suppression remains private and temporary without acquiring death-reset semantics`() {
        val definition = checkNotNull(ServerCacheManager.getVarp(VARP.asRSCM()))
        assertEquals(VarpLifetime.Temp, definition.scope)
        assertEquals(VarpTransmitLevel.Never, definition.transmit)
        val player = Player().apply { currentMapClock = 100 }
        player.blockRunEnergyRestore(100)
        player.attr.removeIf { it.resetOnDeath }
        assertTrue(player.isRunEnergyRestoreBlocked)
        val restored = Player().apply {
            currentMapClock = 100
            vars.backing.putAll(player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertFalse(restored.isRunEnergyRestoreBlocked)
    }

    companion object {
        private const val VARP = "varp.player_run_restore_blocked_until"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
