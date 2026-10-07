package org.rsmod.content.quest.area.icemountain

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.varrock.ShieldOfArrav
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.rewards
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class MarleyDialogueTest {
    @OptIn(InternalApi::class)
    @Test fun `real completion takes precedence over stale sandwich and recruitment state`() {
        for (track in listOf(0, BelowIceMountainTracks.MarleyWantsSandwich, BelowIceMountainTracks.Recruited)) {
            for (symbol in listOf(BelowIceMountainRefs.MarleyEdgeville, BelowIceMountainRefs.MarleyEntrance, BelowIceMountainRefs.MarleyLeaf)) {
                val f = Fixture()
                f.quest.jumpToStage(f.player, BelowIceMountainStage.Complete)
                VarPlayerIntMapSetter.set(f.player, "varbit.bim_marley", track)
                f.talk(symbol)
                assertTrue(f.player.ui.containsModal("interface.chat_right"))
                f.coroutine.resumeWith(ResumePauseButtonInput("component.chat_right:continue", -1))
                assertTrue(f.text().any { "my old pal" in it })
                assertTrue(f.text().none { "sandwich" in it.lowercase() })
                f.coroutine.resumeWith(ResumePauseButtonInput("component.chat_left:continue", -1))
                checkNotNull(f.completion).getOrThrow()
                assertEquals(track, f.player.vars["varbit.bim_marley"])
                assertEquals(BelowIceMountainStage.Complete, f.quest.getQuestStage(f.player))
            }
        }
    }

    @Test fun `unfinished quest still asks for the sandwich`() {
        val f = Fixture()
        f.quest.jumpToStage(f.player, BelowIceMountainStage.CrewNamed)
        VarPlayerIntMapSetter.set(f.player, "varbit.bim_marley", BelowIceMountainTracks.MarleyWantsSandwich)
        f.talk(BelowIceMountainRefs.MarleyEdgeville)
        assertTrue(f.text().any { "Have you got the sandwich yet?" in it })
        assertFalse(f.quest.isQuestCompleted(f.player))
    }

    private class Fixture {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3098, 3469)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        val quest = Quest(
            id = 0, key = BelowIceMountainRefs.QuestKey, rowID = 0,
            displayName = "Below Ice Mountain", mapElement = null, startCoord = null,
            maxSteps = BelowIceMountainStage.Complete, questPoints = 1,
            questVarp = BelowIceMountainRefs.QuestVarp, questVarbit = BelowIceMountainRefs.StageVarbit,
            rewards = rewards {}, itemDisplay = null,
        )
        val coroutine = GameCoroutine("marley-dialogue-test")
        var completion: Result<Unit>? = null
        init {
            with(BelowIceMountainDialogue(quest, unused<BelowIceMountainCutscene>(), unused<ObjRepository>(), unused<ShieldOfArrav>())) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).register()
            }
        }

        @OptIn(InternalApi::class)
        fun talk(symbol: String) {
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getRandom = { DefaultGameRandom(1) },
                getAlignment = { TextAlignment() },
                getNpcInteractions = { NpcInteractions(events) },
            )
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val block: suspend () -> Unit = {
                assertTrue(events.publish(access, NpcEvents.Op1(Npc(symbol, CoordGrid(3098, 3469)))))
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
        }
        fun text() = client.messages.filterIsInstance<IfSetText>().map { it.text }
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
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
