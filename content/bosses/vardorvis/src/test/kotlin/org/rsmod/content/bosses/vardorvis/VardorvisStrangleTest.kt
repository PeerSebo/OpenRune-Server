package org.rsmod.content.bosses.vardorvis

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class VardorvisStrangleTest {
    @Test
    fun `finishing early cannot let the old timeout resolve a later strangle`() {
        val fixture = Fixture()
        var first = 0
        var second = 0
        fixture.script.beginStrangle(fixture.player) { first++ }
        fixture.clearAll()
        assertEquals(1, first)
        fixture.advance(3)
        fixture.script.beginStrangle(fixture.player) { second++ }
        fixture.advance(2)
        assertEquals(0, second)
        assertEquals(4, fixture.player.vars[STATE])
        fixture.advance(3)
        assertEquals(1, second)
        assertEquals(0, fixture.player.vars[STATE])
    }

    @Test
    fun `logout removes strangle ownership and prevents the timeout damaging the old player`() {
        val fixture = Fixture()
        var resolved = 0
        fixture.script.beginStrangle(fixture.player) { resolved++ }
        fixture.events.publish(SessionStateEvent.Logout(fixture.player))
        assertEquals(1, resolved)
        assertEquals(0, fixture.player.vars[STATE])
        fixture.advance(6)
        assertEquals(1, resolved)
        assertTrue(fixture.player.queueList.isEmpty)
    }

    @Test
    fun `invalid spore counts fail before rooting or creating a session`() {
        val fixture = Fixture()
        for (spores in listOf(0, 7)) {
            assertThrows<IllegalArgumentException> {
                fixture.script.beginStrangle(fixture.player, spores)
            }
            assertEquals(0, fixture.player.vars[STATE])
            assertTrue(fixture.queues.isEmpty)
        }
    }

    @Test
    fun `pending difficulty and QTE scalar state are server only temporary vars`() {
        for (symbol in listOf(STATE, "varp.vardorvis_pending_awakened", "varp.overhead_lock_expiration")) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    private class Fixture {
        val events = EventBus()
        val queues = WorldQueueList()
        val script = VardorvisStrangle(queues, NoopPlayerHitModifier)
        val player = Player().apply { slotId = 1; currentMapClock = 100 }

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(script) { context.startup() }
        }

        fun clearAll() {
            for (n in 1..4) VarPlayerIntMapSetter.set(player, "varbit.vardorvis_qte_$n", 0)
            val finish = VardorvisStrangle::class.java.getDeclaredMethod("finishStrangle", Player::class.java)
                .apply { isAccessible = true }
            finish.invoke(script, player)
        }

        fun advance(ticks: Int) {
            repeat(ticks) {
                player.currentMapClock++
                val iterator = queues.iterator()
                while (iterator.hasNext()) {
                    val queue = iterator.next()
                    queue.remainingCycles--
                    if (queue.remainingCycles <= 0) {
                        iterator.remove()
                        queue.action()
                    }
                }
                iterator.cleanUp()
            }
        }
    }

    companion object {
        private const val STATE = "varp.vardorvis_strangle_spores"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
