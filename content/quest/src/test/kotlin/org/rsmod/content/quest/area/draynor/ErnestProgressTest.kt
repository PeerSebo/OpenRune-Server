package org.rsmod.content.quest.area.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ErnestProgressTest {
    @Test fun `journal lists every held missing and banked part independently`() {
        for (mask in 0..7) {
            val f = Fixture()
            for ((index, part) in Parts.withIndex()) {
                if (mask and (1 shl index) != 0) f.player.inv[index] = InvObj(part)
            }
            val journal = f.script.questLog(f.access)
            for ((index, part) in Parts.withIndex()) {
                val name = part.removePrefix("obj.").replace('_', ' ')
                val held = mask and (1 shl index) != 0
                assertEquals(held, journal.contains("I have found the <red>$name</red>."))
                assertEquals(!held, journal.contains("I still need to find the <red>$name</red>."))
            }
        }
        val f = Fixture()
        f.access.bank[0] = InvObj("obj.rubber_tube")
        assertTrue(f.script.questLog(f.access).contains("I have found the <red>rubber tube</red>."))
        f.access.bank[0] = null
        assertTrue(f.script.questLog(f.access).contains("I still need to find the <red>rubber tube</red>."))
    }

    @Test fun `collection objectives wait for the introduction and disappear after hand in`() {
        val f = Fixture()
        f.script.quest.boolean("FOUND_ERNEST", "varp.ernest_found", "varp.ernest_found_migrated").set(f.player, false)
        assertFalse(f.script.questLog(f.access).contains("I still need to find"))
        f.script.quest.boolean("FOUND_ERNEST", "varp.ernest_found", "varp.ernest_found_migrated").set(f.player, true)
        f.access.vars["varbit.ernesthandin_complete"] = 1
        val journal = f.script.questLog(f.access)
        assertTrue(journal.contains("I gave all three parts"))
        assertFalse(journal.contains("I still need to find"))
    }

    @Test fun `closing thanks dialogue preserves completion scroll after modal cleanup`() {
        val f = Fixture()
        f.player.activeCoroutine = f.access.coroutine
        var failure: Throwable? = null
        val scene: suspend () -> Unit = {
            f.script.restoreErnest(f.access) {
                f.access.startDialogue { chatPlayer(happy, "Thank you.") }
            }
        }
        scene.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
        })
        assertTrue(f.access.coroutine.isSuspended)
        f.player.ifClose(f.events)
        assertInstanceOf(CancellationException::class.java, failure)
        assertFalse(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.script.quest.boolean("PENDING_COMPLETION", "varp.ernest_completion_pending", "varp.ernest_completion_pending_migrated").get(f.player))
        f.deliverCompletion()
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertEquals(300, f.player.inv.count("obj.coins"))
        assertEquals(4, f.player.vars["varp.qp"])
        assertTrue(f.script.quest.isQuestCompleted(f.player))
        f.deliverCompletion()
        assertEquals(300, f.player.inv.count("obj.coins"))
        assertEquals(4, f.player.vars["varp.qp"])
    }

    @Test fun `saved pending completion can finish on the next session`() {
        val original = Fixture()
        runScene { original.script.restoreErnest(original.access) {} }
        val resumed = Fixture()
        resumed.player.attr[QUEST_STAGE_MAP_ATTR] = original.player.attr[QUEST_STAGE_MAP_ATTR]!!.toMutableMap()
        resumed.script.quest.boolean("PENDING_COMPLETION", "varp.ernest_completion_pending", "varp.ernest_completion_pending_migrated").set(resumed.player, true)
        resumed.deliverCompletion()
        assertTrue(resumed.player.ui.containsModal("interface.questscroll"))
        assertEquals(300, resumed.player.inv.count("obj.coins"))
    }

    private class Fixture {
        val events = EventBus()
        val player = Player().apply {
            client = RecordingClient()
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
            currentMapClock = 100
            processedMapClock = 100
        }
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )
        val access = ProtectedAccess(player, GameCoroutine(), context)
        val script: ErnestTheChicken
        init {
            val collision = CollisionFlagMap()
            val npcs = NpcList()
            script = ErnestTheChicken(WorldRepository(ZoneUpdateMap()),
                NpcRepository(MapClock(), NpcRegistry(npcs, collision, events), npcs), collision)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(script) { scripts.startup() }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
            script.quest.jumpToStage(player, 1)
            script.quest.boolean("FOUND_ERNEST", "varp.ernest_found", "varp.ernest_found_migrated").set(player, true)
        }
        fun deliverCompletion() {
            val fresh = ProtectedAccess(player, GameCoroutine(), context)
            runScene {
                assertTrue(events.publish(fresh,
                    PlayerQueueEvents.Protected<Nothing?>(null, "queue.ernest_complete".asRSCM())))
            }
        }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val Parts = listOf("obj.rubber_tube", "obj.pressure_gauge", "obj.oil_can")

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private fun runScene(action: suspend () -> Unit) {
            var result: Result<Unit>? = null
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            checkNotNull(result).getOrThrow()
        }
    }
}
