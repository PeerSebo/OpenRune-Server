package org.rsmod.content.skills.magic.spell.attacks.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class VengeanceStateTest {
    @Test fun `clock zero is a real cast and the cooldown ends exactly fifty ticks later`() {
        for (clock in listOf(0, 100, 10000)) {
            val player = Player()
            assertTrue(Vengeance.canCast(player, clock))
            assertFalse(Vengeance.isArmed(player))
            Vengeance.arm(player, clock)
            assertTrue(Vengeance.isArmed(player))
            assertFalse(Vengeance.canCast(player, clock + 49))
            assertTrue(Vengeance.canCast(player, clock + 50))
            Vengeance.disarm(player)
            assertFalse(Vengeance.isArmed(player))
            assertFalse(Vengeance.canCast(player, clock + 49))
        }
    }

    @Test fun `death disarms without resetting cooldown while session events clear both`() {
        val player = Player()
        val script = VengeanceStateScript()
        val events = EventBus()
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        Vengeance.arm(player, 100)
        script.cleanup(player)
        assertFalse(Vengeance.isArmed(player))
        assertFalse(Vengeance.canCast(player, 149))
        assertTrue(Vengeance.canCast(player, 150))
        Vengeance.arm(player, 200)
        assertTrue(events.publish(SessionStateEvent.Logout(player)))
        assertFalse(Vengeance.isArmed(player))
        assertTrue(Vengeance.canCast(player, 0))
        Vengeance.arm(player, 300)
        assertTrue(events.publish(SessionStateEvent.Login(player)))
        assertFalse(Vengeance.isArmed(player))
        assertTrue(Vengeance.canCast(player, 0))
    }

    @Test fun `armed status and cooldown are temporary server vars`() {
        for (name in listOf("varp.vengeance_armed", "varp.vengeance_last_cast")) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
