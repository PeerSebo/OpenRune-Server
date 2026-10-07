package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
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
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.npcs.person.MonasteryMonks
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MonasteryMonksTest {
    @Test fun `native healers restore the current HP fraction and preserve full or boosted health`() {
        for (symbol in MonasteryMonks.HealingMonks + Abbot) {
            val results = if (symbol == Abbot) listOf(10 to 15, 49 to 50, 50 to 50, 55 to 55)
                else listOf(10 to 14, 49 to 50, 50 to 50, 55 to 55)
            for ((current, expected) in results) {
                val f = Fixture(current)
                f.talk(symbol, listOf(1))
                assertEquals(expected, f.player.hitpoints, "$symbol at $current HP")
                assertEquals(0, f.player.vars[MonasteryMonks.Membership])
            }
        }
    }

    @Test fun `sanctuary topic does not heal or join the monastery`() {
        for (symbol in MonasteryMonks.HealingMonks + Abbot) {
            val f = Fixture()
            f.talk(symbol, listOf(2))
            assertEquals(10, f.player.hitpoints)
            assertEquals(0, f.player.vars[MonasteryMonks.Membership])
            assertTrue(f.output().contains("peace and quiet"))
        }
    }

    @Test fun `ordinary monks cannot heal on PvP or Deadman worlds while Abbot can`() {
        for (world in listOf("varbit.this_is_a_pvp_world", "varbit.deadman_mode")) {
            for (symbol in MonasteryMonks.HealingMonks + Abbot) {
                val f = Fixture()
                f.setVar(world, 1)
                f.talk(symbol, listOf(1))
                assertEquals(if (symbol == Abbot) 15 else 10, f.player.hitpoints, "$world $symbol")
            }
        }
    }

    @Test fun `joining needs base Prayer 31 and does not depend on current prayer points`() {
        val low = Fixture(prayer = 30, currentPrayer = 99)
        low.talk(Abbot, listOf(3, 1))
        assertEquals(0, low.player.vars[MonasteryMonks.Membership])
        assertTrue(low.output().contains("Level 31 Prayer"))

        val eligible = Fixture(prayer = 31, currentPrayer = 0)
        eligible.talk(Abbot, listOf(3, 1))
        assertEquals(1, eligible.player.vars[MonasteryMonks.Membership])
        eligible.clearMessages()
        eligible.talk(Abbot, listOf(2))
        assertFalse(eligible.output().contains("May I go upstairs?"))
    }

    @Test fun `declining membership leaves the player unjoined`() {
        val f = Fixture(prayer = 31)
        f.talk(Abbot, listOf(3, 2))
        assertEquals(0, f.player.vars[MonasteryMonks.Membership])
    }

    @Test fun `monastery ladder checks membership and cannot bypass the Prayer requirement`() {
        val low = Fixture(prayer = 30, currentPrayer = 99)
        low.ladder()
        low.finish(listOf(1))
        assertEquals(0, low.player.coords.level)
        assertEquals(0, low.player.vars[MonasteryMonks.Membership])

        val eligible = Fixture(prayer = 31)
        eligible.ladder()
        eligible.finish(listOf(1))
        assertEquals(1, eligible.player.vars[MonasteryMonks.Membership])
        assertEquals(0, eligible.player.coords.level)
        eligible.ladder()
        assertFalse(eligible.coroutine.isAwaiting(ResumePauseButtonInput::class))
        assertTrue(eligible.player.pendingSequence.id > 0)
        eligible.player.cancelActiveCoroutine()
    }

    @Test fun `Ardougne and quest monks are not bound to this healer service`() {
        val f = Fixture()
        for (symbol in listOf("npc.monk_ardougne", "npc.devious_monk_hooded_visable")) {
            val npc = Npc(symbol, Start.translateZ(1))
            assertNull(f.npcInteractions.opTrigger(f.player, npc, InteractionOp.Op1), symbol)
        }
    }

    private class Fixture(currentHp: Int = 10, prayer: Int = 1, currentPrayer: Int = prayer) {
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = Start
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 50)
            statMap.setCurrentLevel("stat.hitpoints", currentHp.toByte())
            statMap.setBaseLevel("stat.prayer", prayer.toByte())
            statMap.setCurrentLevel("stat.prayer", currentPrayer.toByte())
        }
        private val events = EventBus()
        val npcInteractions = NpcInteractions(events)
        private val locInteractions = LocInteractions(BoundValidator(CollisionFlagMap()), events)
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getNpcInteractions = { npcInteractions },
        )
        lateinit var coroutine: GameCoroutine
        private var completion: Result<Unit>? = null

        init { with(MonasteryMonks()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() } }

        fun output() = client.messages.joinToString("\n")
        fun clearMessages() = client.messages.clear()
        fun setVar(symbol: String, value: Int) {
            VarPlayerIntMapSetter.set(player, checkNotNull(ServerCacheManager.getVarbit(symbol.asRSCM())), value)
        }
        fun talk(symbol: String, choices: List<Int>) {
            val npc = Npc(symbol, Start.translateZ(1))
            start(checkNotNull(npcInteractions.opTrigger(player, npc, InteractionOp.Op1)))
            finish(choices)
        }
        fun ladder() {
            val type = checkNotNull(ServerCacheManager.getObject("loc.monasteryladder".asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, Start.translateZ(1), LocEntity(type.id, 10, 0)), type)
            start(checkNotNull(locInteractions.opTrigger(player, loc, InteractionOp.Op1)))
        }
        private fun start(event: OpEvent) {
            completion = null
            coroutine = GameCoroutine("monastery-dialogue")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
        }
        fun finish(choices: List<Int>) {
            val pending = ArrayDeque(choices)
            repeat(25) {
                completion?.let { it.getOrThrow(); assertTrue(pending.isEmpty()); return }
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
                val button = when {
                    player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                    player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                    player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                    player.ui.containsModal("interface.messagebox") -> "component.messagebox:continue"
                    else -> error("Unexpected dialogue interface")
                }
                coroutine.resumeWith(ResumePauseButtonInput(button,
                    if (button.endsWith(":options")) pending.removeFirst() else -1))
            }
            error("Dialogue did not finish")
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
        private const val Abbot = "npc.abbot_langley"
        private val Start = CoordGrid(3059, 3484)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
