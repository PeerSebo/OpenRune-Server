package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.varp.VarpLifetime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.SheepShearer
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class QuestJournalStateTest {
    @Test
    fun `login imports the previous journal key once and preserves quest progress through reload`() {
        val fixture = Fixture()
        for (key in listOf("quest_runemysteries", "  QUEST_RUNEMYSTERIES  ")) {
            val player = player()
            fixture.runes.quest.jumpToStage(player, 1)
            player.attr[Legacy] = key

            fixture.events.publish(SessionStateEvent.Login(player))

            assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(player))
            assertEquals(fixture.runes.quest.id + 1, player.vars[Selection])
            assertEquals(1, player.vars[Migrated])
            assertNull(player.attr[Legacy])
            val resumed = reload(player)
            resumed.attr[Legacy] = "quest_sheepshearer"
            fixture.events.publish(SessionStateEvent.Login(resumed))
            assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(resumed))
            assertEquals(1, fixture.runes.quest.getQuestStage(resumed))
            assertNull(resumed.attr[Legacy])
        }
    }

    @Test
    fun `every native quest key keeps its cache id even before its content is implemented`() {
        Fixture()
        for (row in QuestRow.all()) {
            val key = RSCM.getReverseMapping(RSCMType.DBROW, row.rowId).removePrefix("dbrow.")
            val player = player()
            player.attr[Legacy] = key

            QuestJournalState.migrate(player)

            assertEquals(row.id + 1, player.vars[Selection], key)
            assertEquals(Quest.getById(row.id), QuestJournalRegistry.activeQuest(player), key)
            assertEquals(row.id + 1, reload(player).vars[Selection], key)
            assertNull(player.attr[Legacy], key)
        }
    }

    @Test
    fun `explicit zero and current selections override conflicting legacy keys`() {
        val fixture = Fixture()
        for (value in listOf(0, fixture.sheep.quest.id + 1)) {
            val player = player()
            VarPlayerIntMapSetter.set(player, Selection, value)
            player.attr[Legacy] = "quest_runemysteries"

            QuestJournalState.migrate(player)

            assertEquals(value, player.vars[Selection])
            assertEquals(if (value == 0) null else fixture.sheep.quest,
                QuestJournalRegistry.activeQuest(player))
            assertEquals(value, reload(player).vars[Selection])
            assertNull(player.attr[Legacy])
        }
    }

    @Test
    fun `clearing blocks stale imports and malformed or deleted keys remain harmless`() {
        Fixture()
        val cleared = player().apply { attr[Legacy] = "quest_runemysteries" }
        QuestJournalState.clear(cleared)
        val resumed = reload(cleared).apply { attr[Legacy] = "quest_sheepshearer" }
        assertNull(QuestJournalRegistry.activeQuest(resumed))
        assertEquals(0, resumed.vars[Selection])
        assertNull(resumed.attr[Legacy])

        for (legacy in listOf("quest_no_longer_installed", "", 123, false)) {
            val player = player().apply { attr[Legacy] = legacy }
            assertNull(QuestJournalRegistry.activeQuest(player))
            assertEquals(1, player.vars[Migrated])
            assertNull(player.attr[Legacy])
            assertNull(QuestJournalRegistry.activeQuest(reload(player)))
        }
        for (value in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val player = player()
            VarPlayerIntMapSetter.set(player, Selection, value)
            assertNull(QuestJournalRegistry.activeQuest(player))
            assertEquals(value, player.vars[Selection])
        }
    }

    @Test
    fun `real journal buttons switch close and reopen the saved selection with native scripts`() {
        val fixture = Fixture()
        val player = player()
        val client = player.client as RecordingClient
        fixture.press(player, "component.questlist:list", fixture.runes.quest.id)

        assertTrue(player.ui.containsOverlay("interface.questjournal_overview"))
        assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(player))
        fixture.press(player, "component.questjournal_overview:switch")
        assertTrue(player.ui.containsOverlay("interface.questjournal"))
        fixture.press(player, "component.questjournal:close")
        assertFalse(player.ui.containsOverlay("interface.questjournal"))
        assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(player))

        val ids = client.messages.filterIsInstance<RunClientScript>().map { it.id }
        assertTrue("clientscript.[clientscript,questjournal_overview_setup]".asRSCM() in ids)
        assertTrue("clientscript.[clientscript,quest_journal_reset]".asRSCM() in ids)
        assertTrue("clientscript.[clientscript,script2523]".asRSCM() in ids)

        val resumed = reload(player)
        fixture.events.publish(SessionStateEvent.Login(resumed))
        fixture.press(resumed, "component.questjournal:switch")
        assertTrue(resumed.ui.containsOverlay("interface.questjournal_overview"))
        assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(resumed))
    }

    @Test
    fun `cancelling surrounding work and resetting the quest retain the last journal selection`() {
        val fixture = Fixture()
        val player = player()
        val coroutine = player.launch {
            QuestJournalRegistry.openJournal(fixture.access(player), fixture.runes.quest, JournalState.OVERVIEW)
            pause { false }
        }
        coroutine.cancel()
        player.ifCloseOverlay("interface.questjournal_overview", fixture.events)
        fixture.runes.quest.resetQuest(player)

        assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(player))
        assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(reload(player)))
        fixture.press(player, "component.questlist:list", fixture.sheep.quest.id)
        assertEquals(fixture.sheep.quest, QuestJournalRegistry.activeQuest(reload(player)))
    }

    @Test
    fun `activity journals dispatch through the native list without changing quest progress`() {
        val fixture = Fixture()
        val player = player()
        fixture.runes.quest.jumpToStage(player, 1)
        fixture.press(player, "component.questlist:list", fixture.runes.quest.id)
        val points = player.vars["varp.qp"]
        val row = QuestRow.getRow("dbrow.quest_tearsofguthix")
        var opened = false
        QuestJournalRegistry.registerStatus(row.id) { access ->
            opened = true
            access.ifOpenOverlay("interface.questjournal")
        }

        fixture.press(player, "component.questlist:list", row.id)

        assertTrue(opened)
        assertTrue(player.ui.containsOverlay("interface.questjournal"))
        assertNull(QuestJournalRegistry.activeQuest(player))
        assertEquals(1, fixture.runes.quest.getQuestStage(player))
        assertEquals(points, player.vars["varp.qp"])
        fixture.press(player, "component.questlist:list", fixture.runes.quest.id)
        assertEquals(fixture.runes.quest, QuestJournalRegistry.activeQuest(player))
    }

    private class Fixture {
        val events = EventBus()
        val runes = RuneMysteriesQuest()
        val sheep = SheepShearer()
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(runes) { scripts.startup() }
            with(sheep) { scripts.startup() }
            with(QuestJournalScript()) { scripts.startup() }
        }

        fun access(player: Player) = ProtectedAccess(player, GameCoroutine(), context)

        fun press(player: Player, component: String, sub: Int = -1) {
            val event = IfOverlayButton(ServerCacheManager.fromComponent(component.asRSCM()), sub, null, IfButtonOp.Op1)
            runScene { assertTrue(events.publish(access(player), event)) }
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
        private const val Selection = "varp.active_quest_journal"
        private const val Migrated = "varp.active_quest_journal_migrated"
        private val Legacy = AttributeKey<Any>("active_quest_journal")

        private fun player() = Player().apply {
            client = RecordingClient()
            inv = invMap.getOrPut("inv.inv")
            invMap.getOrPut("inv.bank")
        }

        private fun reload(original: Player) = player().apply {
            vars.backing.putAll(original.vars.backing.filterKeys { ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm })
            attr.putAllFromPersistence(original.attr.toPersistentMap())
        }

        private fun runScene(action: suspend () -> Unit) {
            var result: Result<Unit>? = null
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            checkNotNull(result).getOrThrow()
        }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
