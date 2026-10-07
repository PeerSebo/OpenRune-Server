package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.npcs.person.RowdyDwarf
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RowdyDwarfTest {
    @Test fun `native rowdy dwarf completes his ordinary response without opening another action`() {
        val f = Fixture()
        val event = checkNotNull(f.interactions.opTrigger(f.player, f.npc, InteractionOp.Op1))
        assertEquals(2393, f.npc.type.id)
        assertInstanceOf(NpcUnimplementedEvents.Op1::class.java, event)
        val coroutine = GameCoroutine("rowdy-dwarf-dialogue")
        f.player.activeCoroutine = coroutine
        val access = ProtectedAccess(f.player, coroutine, ProtectedAccessContextFactory.empty().copy(
            getEventBus = { f.events }, getAlignment = { TextAlignment() },
            getNpcInteractions = { f.interactions },
        ))
        var completion: Result<Unit>? = null
        val block: suspend () -> Unit = { assertTrue(f.events.publish(access, event)) }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
        assertTrue(f.player.ui.containsModal("interface.chat_left"))
        assertTrue(f.messages.any { it.toString().contains("Get OUT of my WAY!") })
        coroutine.resumeWith(ResumePauseButtonInput("component.chat_left:continue", -1))
        checkNotNull(completion).getOrThrow()
        assertNull(f.player.interaction)
    }

    @Test fun `an explicit quest handler takes precedence over the ordinary conversation`() {
        val f = Fixture()
        f.scripts.onOpNpc1("npc.dwarf_city_rowdy_dwarf") { }
        val event = f.interactions.opTrigger(f.player, f.npc, InteractionOp.Op1)
        assertInstanceOf(NpcEvents.Op1::class.java, event)
    }

    private class Fixture {
        val messages = mutableListOf<Any>()
        val player = Player().apply {
            coords = CoordGrid(2927, 10197)
            currentMapClock = 100
            processedMapClock = 100
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
            client = object : Client<Any, Any> {
                override fun write(message: Any) { messages += message }
                override fun close() {}
                override fun read(player: Player) {}
                override fun flush() {}
                override fun flushHighPriority() {}
                override fun unregister(service: Any, player: Player) {}
            }
        }
        val npc = Npc("npc.dwarf_city_rowdy_dwarf", player.coords.translateZ(1))
        val events = EventBus()
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val interactions = NpcInteractions(events)
        init { with(RowdyDwarf()) { scripts.startup() } }
    }

    companion object {
        @JvmStatic @BeforeAll fun loadNativeDefinitions() { ServerCacheManager.init(241).close() }
    }
}
