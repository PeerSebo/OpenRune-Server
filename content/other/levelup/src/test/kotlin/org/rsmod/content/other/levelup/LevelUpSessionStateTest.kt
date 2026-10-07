package org.rsmod.content.other.levelup

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import dev.or2.central.account.Rights
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.EngineQueueEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifOpenTop
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.EngineQueueType
import org.rsmod.game.stat.StatAdvance
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class LevelUpSessionStateTest {
    @Test fun `jingle preview starts at zero and rejects invalid ids without advancing`() {
        val f = Fixture()
        assertFalse(Jingle in f.player.vars)
        f.jingle()
        assertTrue(Jingle in f.player.vars)
        assertEquals(0, f.player.vars[Jingle])
        f.jingle()
        assertEquals(1, f.player.vars[Jingle])
        for (input in listOf("-1", "65535")) {
            f.jingle(input)
            assertEquals(1, f.player.vars[Jingle])
        }
        f.jingle("65534")
        f.jingle()
        assertEquals(65534, f.player.vars[Jingle])
    }

    @Test fun `login snapshot reports one combat increase and no duplicate on the next skill advance`() {
        val f = Fixture()
        assertTrue(f.events.publish(SessionStateEvent.Login(f.player)))
        assertEquals(3, f.player.vars[Combat])
        f.player.appearance.combatLevel = 4
        f.advance()
        assertEquals(4, f.player.vars[Combat])
        assertEquals(1, f.combatMessages())
        f.advance()
        assertEquals(1, f.combatMessages())
    }

    @Test fun `missing combat snapshot does not fabricate a gain and session fields do not save`() {
        val f = Fixture()
        f.player.appearance.combatLevel = 0
        f.advance()
        assertTrue(Combat in f.player.vars)
        assertEquals(0, f.player.vars[Combat])
        assertEquals(0, f.combatMessages())
        f.jingle()
        val restored = Player().apply {
            vars.backing.putAll(f.player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        for (symbol in listOf(Combat, Jingle)) {
            assertFalse(symbol in restored.vars)
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    private class Fixture {
        val events = EventBus()
        private val commands = CheatCommandMap()
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            modLevel = Rights.ADMINISTRATOR
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            ifOpenTop("interface.toplevel_osrs_stretch")
        }
        private val world = WorldRepository(ZoneUpdateMap())
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() }, getWorldRepo = { world },
        )
        init {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run { isAccessible = true; get(null) as Unsafe }
            val factory = unsafe.allocateInstance(ProtectedAccessContextFactory::class.java) as ProtectedAccessContextFactory
            with(LevelUpScript(world, ProtectedAccessLauncher(factory))) {
                ScriptContext(events, commands, EngineQueueCache()).startup()
            }
        }
        fun jingle(vararg args: String) { assertTrue(commands.execute(player, "jingle", args.toList())) }
        fun combatMessages() = client.messages.filterIsInstance<MessageGame>().count {
            it.message == "Congratulations, you just advanced a Combat level."
        }
        fun advance() {
            val coroutine = GameCoroutine()
            player.activeCoroutine = coroutine
            var outcome: Result<Unit>? = null
            val action: suspend () -> Unit = {
                val stat = ServerCacheManager.getStats().values.single { it.internalName == "stat.attack" }
                val event = EngineQueueEvents.Default(StatAdvance(stat, 2), EngineQueueType.AdvanceStat.id)
                assertTrue(events.publish(ProtectedAccess(player, coroutine, context), event))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { outcome = result }
            })
            outcome?.getOrThrow()
            coroutine.cancel()
        }
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
        private const val Combat = "varp.levelup_last_combat_level"
        private const val Jingle = "varp.levelup_last_jingle_id"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
