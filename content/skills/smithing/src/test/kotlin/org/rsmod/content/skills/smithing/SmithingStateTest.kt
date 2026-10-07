package org.rsmod.content.skills.smithing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class SmithingStateTest {
    @Test fun `quantity preserves one all and custom amounts but resets across sessions`() {
        val player = Player()
        val events = events()
        assertEquals(1, SmithingState.quantity(player))
        for (quantity in listOf(1, 5, 10, -1, 127, Int.MAX_VALUE)) {
            SmithingState.setQuantity(player, quantity)
            assertEquals(quantity, SmithingState.quantity(player))
        }
        assertTrue(events.publish(SessionStateEvent.Logout(player)))
        assertEquals(1, SmithingState.quantity(player))
        SmithingState.setQuantity(player, -1)
        assertTrue(events.publish(SessionStateEvent.Login(player)))
        assertEquals(1, SmithingState.quantity(player))
    }

    @Test fun `crystal preference imports both legacy values and persists without the old key`() {
        for (skip in listOf(false, true)) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(Legacy to skip, "unrelated" to 3))
            assertTrue(events().publish(SessionStateEvent.Login(player)))
            assertEquals(skip, SmithingState.skipCrystalConfirmation(player))
            assertEquals(mapOf("unrelated" to 3), player.attr.toPersistentMap())
            val restored = Player().apply {
                vars.backing.putAll(player.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                })
            }
            assertEquals(skip, SmithingState.skipCrystalConfirmation(restored))
            assertEquals(1, SmithingState.quantity(restored))
        }
    }

    @Test fun `explicit false and completed migration defeat stale legacy confirmation`() {
        for (symbol in listOf(Confirmation, Migrated)) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, symbol, if (symbol == Migrated) 1 else 0)
            player.attr.putAllFromPersistence(mapOf(Legacy to true))
            assertFalse(SmithingState.skipCrystalConfirmation(player))
            assertTrue(player.attr.toPersistentMap().isEmpty())
        }
        val player = Player()
        SmithingState.setSkipCrystalConfirmation(player, true)
        assertTrue(SmithingState.skipCrystalConfirmation(player))
        SmithingState.setSkipCrystalConfirmation(player, false)
        player.attr.putAllFromPersistence(mapOf(Legacy to true))
        assertFalse(SmithingState.skipCrystalConfirmation(player))
    }

    @Test fun `only crystal confirmation is permanent and all custom vars stay server side`() {
        for (name in listOf(Quantity, Confirmation, Migrated)) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(if (name == Quantity) VarpLifetime.Temp else VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    private fun events() = EventBus().also {
        with(SmithingStateScript()) { ScriptContext(it, CheatCommandMap(), EngineQueueCache()).startup() }
    }

    companion object {
        private const val Quantity = "varp.smithing_quantity"
        private const val Confirmation = "varp.crystal_singing_skip_confirmation"
        private const val Migrated = "varp.crystal_singing_confirmation_migrated"
        private const val Legacy = "crystal_singing_dont_ask_again"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
