package org.rsmod.content.skills.thieving.scripts

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.content.skills.thieving.ThievingModule
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class BlackjackStateTest {
    @Test
    fun `fresh characters have no guaranteed pickpocket`() {
        val player = player()

        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, npc()))
        assertCleared(player)
    }

    @Test
    fun `last credit succeeds then clears target and expiry`() {
        val player = player()
        val target = npc()
        BlackjackState.grant(player, target)

        assertEquals(2, player.vars[REMAINING])
        assertEquals(target.uid.packed, player.vars[TARGET_UID])
        assertEquals(108, player.vars[EXPIRES_AT])
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, target))
        assertEquals(1, player.vars[REMAINING])
        assertEquals(target.uid.packed, player.vars[TARGET_UID])
        assertEquals(108, player.vars[EXPIRES_AT])
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, target))
        assertCleared(player)
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, target))
    }

    @Test
    fun `another NPC cannot consume or clear unexpired credit`() {
        val player = player()
        val target = npc()
        BlackjackState.grant(player, target)

        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, npc(slot = 2)))
        assertEquals(2, player.vars[REMAINING])
        assertEquals(target.uid.packed, player.vars[TARGET_UID])
        assertEquals(108, player.vars[EXPIRES_AT])
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, target))
    }

    @Test
    fun `replacement with a changed UID cannot inherit knockout credit`() {
        val player = player()
        val target = npc()
        BlackjackState.grant(player, target)
        target.clearUid()

        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, target))
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, npc(type = 2)))
        assertEquals(2, player.vars[REMAINING])
    }

    @Test
    fun `packed UID retains all bits when its signed value is negative`() {
        val player = player()
        val target = npc(type = 40_000)
        assertTrue(target.uid.packed < 0)
        BlackjackState.grant(player, target)

        assertEquals(target.uid.packed, player.vars[TARGET_UID])
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, npc(type = 7_232)))
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, target))
    }

    @Test
    fun `credit succeeds before expiry and fails exactly at the expiry tick`() {
        val player = player()
        val target = npc()
        BlackjackState.grant(player, target)
        player.currentMapClock += ThievingRates.BLACKJACK_KO_CYCLES - 1

        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, target))
        player.currentMapClock++
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, target))
        assertCleared(player)
    }

    @Test
    fun `expired credit clears even when a different NPC is pickpocketed`() {
        val player = player()
        BlackjackState.grant(player, npc())
        player.currentMapClock += ThievingRates.BLACKJACK_KO_CYCLES + 1

        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, npc(slot = 2)))
        assertCleared(player)
    }

    @Test
    fun `new knockout replaces the target and refreshes credit and expiry`() {
        val player = player()
        val first = npc()
        val second = npc(slot = 2)
        BlackjackState.grant(player, first)
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, first))
        player.currentMapClock += 3
        BlackjackState.grant(player, second)

        assertEquals(2, player.vars[REMAINING])
        assertEquals(111, player.vars[EXPIRES_AT])
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, first))
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, second))
    }

    @Test
    fun `bound death cleanup removes credit without resetting unrelated state`() {
        val player = player()
        val target = npc()
        player.attr.putAllFromPersistence(mapOf("unrelated_state" to 17))
        BlackjackState.grant(player, target)
        val hooks = Guice.createInjector(ThievingModule()).getInstance(
            Key.get(object : TypeLiteral<Set<PlayerDeathCleanupHook>>() {})
        )

        assertEquals(1, hooks.size)
        hooks.forEach { it.cleanup(player) }
        assertCleared(player)
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, target))
        assertEquals(mapOf("unrelated_state" to 17), player.attr.toPersistentMap())
    }

    @Test
    fun `logout discards credit and repeated logout remains safe`() {
        val player = player()
        val target = npc()
        BlackjackState.grant(player, target)
        val events = events()

        repeat(2) { events.publish(SessionStateEvent.Logout(player)) }

        assertCleared(player)
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, target))
    }

    @Test
    fun `login clears stale session state even when the player object is reused`() {
        val player = player()
        val target = npc()
        BlackjackState.grant(player, target)

        events().publish(SessionStateEvent.Login(player))

        assertCleared(player)
        assertFalse(BlackjackState.consumeGuaranteedPickpocket(player, target))
    }

    @Test
    fun `packed state is temporary and never sends client varp updates`() {
        for (name in STATE_VARS) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope, name)
            assertEquals(VarpTransmitLevel.Never, type.transmit, name)
        }
        val client = RecordingClient()
        val player = player().apply {
            this.client = client
            processedMapClock = currentMapClock
        }
        val target = npc()

        BlackjackState.grant(player, target)
        assertTrue(BlackjackState.consumeGuaranteedPickpocket(player, target))
        BlackjackState.clear(player)

        assertTrue(client.messages.isEmpty())
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    private fun player() = Player().apply { currentMapClock = 100 }

    private fun npc(slot: Int = 1, type: Int = 1) = Npc(NpcServerType(id = type)).apply {
        slotId = slot
        assignUid()
    }

    private fun events() = EventBus().also {
        with(BlackjackStateScript()) {
            ScriptContext(it, CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    private fun assertCleared(player: Player) {
        for (name in STATE_VARS) assertEquals(0, player.vars[name], name)
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
        private const val REMAINING = "varp.blackjack_pickpockets_remaining"
        private const val TARGET_UID = "varp.blackjack_target_uid"
        private const val EXPIRES_AT = "varp.blackjack_expires_at"
        private val STATE_VARS = listOf(REMAINING, TARGET_UID, EXPIRES_AT)

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
