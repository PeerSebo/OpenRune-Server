package org.rsmod.api.mechanics.toxins.impl

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

@ResourceLock("ServerCacheManager")
class PlayerPoisonTest {
    @Test fun `stronger poison updates severity without repeating the notification`() {
        val f = Fixture()
        assertTrue(PlayerPoison.tryPoison(f.player, initialDamage = 3))
        assertEquals(10, f.severity)
        assertEquals(1, f.notifications)

        assertTrue(PlayerPoison.tryPoison(f.player, initialDamage = 6))
        assertEquals(25, f.severity)
        assertEquals(1, f.notifications)
    }

    @Test fun `same damage poison refreshes severity silently and weaker poison is rejected`() {
        val f = Fixture()
        assertTrue(PlayerPoison.tryPoison(f.player, initialDamage = 6))
        assertTrue(PlayerPoison.tryPoison(f.player, initialDamage = 6))
        assertEquals(25, f.severity)
        assertFalse(PlayerPoison.tryPoison(f.player, initialDamage = 3))
        assertEquals(25, f.severity)
        assertEquals(1, f.notifications)
    }

    @Test fun `curing poison permits a new infection notification`() {
        val f = Fixture()
        assertTrue(PlayerPoison.tryPoison(f.player, initialDamage = 3))
        PlayerPoison.clear(f.player)
        assertFalse(PlayerPoison.isPoisoned(f.player))

        assertTrue(PlayerPoison.tryPoison(f.player, initialDamage = 3))
        assertEquals(2, f.notifications)
    }

    @Test fun `natural poison expiry permits a new infection notification`() {
        val f = Fixture()
        assertTrue(PlayerPoison.tryPoison(f.player, severity = 2))
        assertTrue(PlayerPoison.isPoisoned(f.player))
        PlayerPoison.onPoisonTimerTick(f.player)
        assertFalse(PlayerPoison.isPoisoned(f.player))

        assertTrue(PlayerPoison.tryPoison(f.player, severity = 2))
        assertEquals(2, f.notifications)
    }

    @Test fun `immunity and venom still prevent poison and its notification`() {
        val immune = Fixture()
        ToxinImmunity.grantImmunity(immune.player, poisonDuration = 100)
        assertFalse(PlayerPoison.tryPoison(immune.player, initialDamage = 6))
        assertEquals(0, immune.severity)
        assertEquals(0, immune.notifications)

        val envenomed = Fixture()
        assertTrue(PlayerVenom.tryVenom(envenomed.player))
        assertFalse(PlayerPoison.tryPoison(envenomed.player, initialDamage = 6))
        assertEquals(0, envenomed.severity)
        assertEquals(0, envenomed.notifications)
    }

    private class Fixture {
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            currentMapClock = 100
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val severity get() = player.vars["varp.poison_severity"]
        val notifications get() = client.messages.count { "You have been poisoned!" in it.toString() }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
