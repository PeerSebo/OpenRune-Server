package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.QuestFlag
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class CooksAssistantStateTest {
    @Test
    fun `existing true varbits survive missing or false legacy flags`() {
        for (legacyValue in listOf(null, false)) {
            val script = CooksAssistant()
            val player = player()
            for (name in names) {
                VarPlayerIntMapSetter.set(player, "varbit.cooks_assistant_given_$name", 1)
                if (legacyValue != null) player.attr[legacy(name)] = legacyValue
            }

            script.quest.migrateFields(player)

            for (name in names) {
                assertTrue(flag(script, name).get(player))
                assertFalse(player.attr.has(legacy(name)))
                assertEquals(1, player.vars["varbit.cooks_assistant_${name}_migrated"])
            }
            assertJournal(script, player, 7)
        }
    }

    @Test
    fun `every old delivery combination survives migration journal rendering and a new session`() {
        for (mask in 0..7) {
            val script = CooksAssistant()
            val player = player()
            for ((index, name) in names.withIndex()) player.attr[legacy(name)] = mask and (1 shl index) != 0

            script.quest.migrateFields(player)

            for ((index, name) in names.withIndex()) {
                assertEquals(mask and (1 shl index) != 0, flag(script, name).get(player), name)
                assertFalse(player.attr.has(legacy(name)), name)
                assertEquals(1, player.vars["varbit.cooks_assistant_${name}_migrated"], name)
            }
            assertJournal(script, player, mask)
            val resumed = player().apply { vars.backing.putAll(player.vars.backing) }
            script.quest.migrateFields(resumed)
            assertJournal(script, resumed, mask)
        }
    }

    @Test
    fun `writing a false flag does not suppress other legacy flags sharing its varp`() {
        val script = CooksAssistant()
        val player = player()
        for (name in names) player.attr[legacy(name)] = true
        flag(script, "egg").set(player, false)

        script.quest.migrateFields(player)

        assertFalse(flag(script, "egg").get(player))
        assertTrue(flag(script, "milk").get(player))
        assertTrue(flag(script, "flour").get(player))
        player.attr[legacy("egg")] = true
        script.quest.migrateFields(player)
        assertFalse(flag(script, "egg").get(player))
        assertFalse(player.attr.has(legacy("egg")))
    }

    @Test
    fun `quest reset clears deliveries without allowing stale attributes to restore them`() {
        val script = CooksAssistant()
        val player = player()
        for (name in names) player.attr[legacy(name)] = true
        script.quest.jumpToStage(player, 1)

        script.quest.resetQuest(player)

        assertEquals(0, script.quest.getQuestStage(player))
        for (name in names) {
            assertFalse(flag(script, name).get(player))
            assertFalse(player.attr.has(legacy(name)))
            player.attr[legacy(name)] = true
        }
        script.quest.migrateFields(player)
        assertJournal(script, player, 0)
    }

    @Test
    fun `login migrates flags before a player needs to read the journal`() {
        val script = CooksAssistant()
        val events = EventBus()
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        val player = player().apply { attr[legacy("milk")] = true }

        events.publish(SessionStateEvent.Login(player))

        assertEquals(1, player.vars["varbit.cooks_assistant_given_milk"])
        assertFalse(player.attr.has(legacy("milk")))
        assertJournal(script, player, 2)
    }

    @Test
    fun `delivery flags have persistent private disjoint cache bits`() {
        val script = CooksAssistant()
        script.quest.assertFieldsResolve()
        val varp = checkNotNull(ServerCacheManager.getVarp("varp.cooks_assistant_deliveries".asRSCM()))
        assertEquals(VarpLifetime.Perm, varp.scope)
        assertFalse(varp.transmit.always || varp.transmit.onDiff)
        val symbols = names.flatMap { listOf("varbit.cooks_assistant_given_$it", "varbit.cooks_assistant_${it}_migrated") }
        val bits = symbols.map { checkNotNull(ServerCacheManager.getVarbit(it.asRSCM())) }
        assertTrue(bits.all { it.varp == varp.id && it.startBit == it.endBit })
        assertEquals((0..5).toSet(), bits.map { it.startBit }.toSet())
    }

    private fun assertJournal(script: CooksAssistant, player: Player, mask: Int) {
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
        val journal = script.questLog(access)
        for ((index, text) in listOf("I have given the cook an egg.", "I have given the cook a bucket of milk.", "I have given the cook a pot of flour.").withIndex()) {
            assertEquals(mask and (1 shl index) != 0, journal.contains("<black><str>$text</str></black>"), "mask=$mask: $text")
        }
    }

    private fun flag(script: CooksAssistant, name: String): QuestFlag = script.quest.flag(
        "GIVEN_${name.uppercase()}", "varbit.cooks_assistant_given_$name", "varbit.cooks_assistant_${name}_migrated",
    )

    private fun legacy(name: String) = AttributeKey<Boolean>("quest.quest_cooksassistant.GIVEN_${name.uppercase()}")

    private fun player() = Player().apply { inv = invMap.getOrPut("inv.inv") }

    companion object {
        private val names = listOf("egg", "milk", "flour")

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
