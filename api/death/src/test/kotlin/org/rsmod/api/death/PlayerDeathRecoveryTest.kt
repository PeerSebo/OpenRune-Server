package org.rsmod.api.death

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class PlayerDeathRecoveryTest {
    @Test fun `ordinary Wilderness and PvP deaths cannot invoke instance redirect hooks`() {
        val instances = listOf(context().copy(inInstance = false), context().copy(inWilderness = true),
            context().copy(recentPvpDamage = true), context().copy(killer = Player()))
        val hooks = setOf(PlayerDeathRedirectHook { error("Unauthorised redirect lookup") })
        for (context in instances) {
            val result = PlayerDeathRecovery.resolve(context, hooks)
            assertSame(context, result.context)
            assertEquals(context.coords, result.coords)
        }
    }

    @Test fun `unclaimed instance death retains the previous selection and location`() {
        val context = context()
        val result = PlayerDeathRecovery.resolve(context, setOf(PlayerDeathRedirectHook { null }))
        assertSame(context, result.context)
        assertEquals(context.coords, result.coords)
    }

    @Test fun `owned arena redirects recovery and preserves account item protection and death facts`() {
        val context = context().copy(gamemode = 3, isSkulled = true, hasProtectItem = true)
        val exit = CoordGrid(1235, 3728)
        val result = PlayerDeathRecovery.resolve(context, setOf(PlayerDeathRedirectHook { if (it === context.player) exit else null }))
        assertEquals(exit, result.coords)
        assertEquals(context.copy(inInstance = false), result.context)
        assertEquals(context.coords, result.context.coords)
        assertSame(context.player, result.context.player)
    }

    private fun context() = PlayerDeathContext(Player(), CoordGrid(6400, 6400), false, -1, false, true,
        false, false, false, 0, null)
}
