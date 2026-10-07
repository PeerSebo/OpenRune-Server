package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class DemonSlayerProgressTest {
    @Test
    fun `all bone counts and Aris answer masks survive login migration and saved var reload`() {
        val f = Fixture()
        for (bones in 0..25) {
            for (mask in 0..15) {
                val answers = mask shl 1
                val player = Player().apply {
                    attr.putAllFromPersistence(mapOf(
                        AskedKey to true, BonesKey to bones, ArisKey to answers, KeyGivenKey to false,
                        "unrelated.progress" to 12,
                    ))
                }
                f.login(player)
                assertEquals(bones, f.progress.traibornBones.get(player))
                assertEquals(answers, f.progress.arisAsked.get(player))
                assertEquals(25 - bones, f.progress.bonesOwed(player))
                assertEquals(12, player.attr.toPersistentMap()["unrelated.progress"])
                assertLegacyRemoved(player)

                val resumed = reload(player)
                f.login(resumed)
                assertEquals(bones, f.progress.traibornBones.get(resumed))
                assertEquals(answers, f.progress.arisAsked.get(resumed))
                assertEquals(25 - bones, f.progress.bonesOwed(resumed))
            }
        }
    }

    @Test
    fun `all Traiborn conversation flags retain the journal bone requirement`() {
        val f = Fixture()
        for (asked in listOf(false, true)) {
            for (given in listOf(false, true)) {
                val player = Player().apply {
                    attr.putAllFromPersistence(mapOf(
                        AskedKey to asked, KeyGivenKey to given, BonesKey to 9,
                    ))
                }
                f.login(player)
                assertEquals(asked, f.progress.traibornAsked.get(player))
                assertEquals(given, f.progress.traibornKeyGiven.get(player))
                assertEquals(if (asked && !given) 16 else 0, f.progress.bonesOwed(player))
                assertLegacyRemoved(player)
            }
        }
    }

    @Test
    fun `explicit zero and partial updates import other fields independently`() {
        val f = Fixture()
        val player = Player().apply {
            VarPlayerIntMapSetter.set(this, "varp.demon_slayer_traiborn_bones", 0)
            attr.putAllFromPersistence(mapOf(
                AskedKey to true, KeyGivenKey to true, BonesKey to 24, ArisKey to 30,
            ))
        }
        f.progress.traibornKeyGiven.set(player, false)
        f.login(player)
        assertEquals(0, f.progress.traibornBones.get(player))
        assertEquals(30, f.progress.arisAsked.get(player))
        assertTrue(f.progress.traibornAsked.get(player))
        assertFalse(f.progress.traibornKeyGiven.get(player))
        assertEquals(25, f.progress.bonesOwed(player))
        assertLegacyRemoved(player)
    }

    @Test
    fun `lost key progress reset stays zero after stale legacy data and relog`() {
        val f = Fixture()
        val player = Player().apply {
            attr.putAllFromPersistence(mapOf(AskedKey to true, KeyGivenKey to true, BonesKey to 25))
        }
        f.login(player)
        f.progress.traibornKeyGiven.set(player, false)
        f.progress.traibornBones.set(player, 0)
        val resumed = reload(player).apply {
            attr.putAllFromPersistence(mapOf(KeyGivenKey to true, BonesKey to 25))
        }
        f.login(resumed)
        assertEquals(0, f.progress.traibornBones.get(resumed))
        assertEquals(25, f.progress.bonesOwed(resumed))
        assertLegacyRemoved(resumed)
    }

    @Test
    fun `administrative quest reset prevents old flags counters and answers from returning`() {
        val f = Fixture()
        val legacy = mapOf(AskedKey to true, KeyGivenKey to true, BonesKey to 25, ArisKey to 30)
        val player = Player().apply { attr.putAllFromPersistence(legacy) }
        f.quest.jumpToStage(player, DemonSlayerStage.KeysNamed)
        f.quest.resetQuest(player)
        val resumed = reload(player).apply { attr.putAllFromPersistence(legacy) }
        f.login(resumed)
        assertEquals(0, f.quest.getQuestStage(resumed))
        assertFalse(f.progress.traibornAsked.get(resumed))
        assertFalse(f.progress.traibornKeyGiven.get(resumed))
        assertEquals(0, f.progress.traibornBones.get(resumed))
        assertEquals(0, f.progress.arisAsked.get(resumed))
        assertEquals(0, f.progress.bonesOwed(resumed))
        assertLegacyRemoved(resumed)
    }

    @Test
    fun `fresh characters retain defaults and progress is isolated per player`() {
        val f = Fixture()
        val first = Player()
        f.progress.traibornAsked.set(first, true)
        f.progress.traibornBones.set(first, 16)
        f.progress.arisAsked.set(first, 30)
        val second = Player()
        f.login(second)
        assertEquals(9, f.progress.bonesOwed(first))
        assertEquals(0, f.progress.bonesOwed(second))
        assertEquals(0, f.progress.traibornBones.get(second))
        assertEquals(0, f.progress.arisAsked.get(second))
        assertFalse(f.progress.traibornKeyGiven.get(second))
        f.quest.assertFieldsResolve()
    }

    private fun assertLegacyRemoved(player: Player) {
        assertTrue(player.attr.toPersistentMap().keys.none {
            it in setOf(AskedKey, BonesKey, KeyGivenKey, ArisKey)
        })
    }

    private fun reload(player: Player) = Player().apply {
        vars.backing.putAll(player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        })
        attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    private class Fixture {
        private val events = EventBus()
        private val script = object : QuestScript(
            DemonSlayerRefs.QuestKey, "varp.demonstart", rewards {},
            questVarbit = DemonSlayerRefs.VarbitStage,
        ) {
            val progress = DemonSlayerProgress(quest)
            override fun ScriptContext.init() {}
            override fun subTitle() = ""
            override fun questLog(player: ProtectedAccess) = ""
            override fun completedLog(player: ProtectedAccess) = ""
        }
        val quest = script.quest
        val progress = script.progress

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun login(player: Player) { events.publish(SessionStateEvent.Login(player)) }
    }

    companion object {
        private const val AskedKey = "quest.quest_demonslayer.TRAIBORN_ASKED"
        private const val BonesKey = "quest.quest_demonslayer.TRAIBORN_BONES"
        private const val KeyGivenKey = "quest.quest_demonslayer.TRAIBORN_KEY_GIVEN"
        private const val ArisKey = "quest.quest_demonslayer.ARIS_ASKED"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
