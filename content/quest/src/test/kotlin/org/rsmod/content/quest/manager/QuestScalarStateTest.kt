package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueDisguise
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueJail
import org.rsmod.content.quest.area.blackknights.BlackKnightsFortress
import org.rsmod.content.quest.area.draynor.ErnestTheChicken
import org.rsmod.content.quest.area.draynor.VampyreSlayerCrypt
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.TheRestlessGhostScenery
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.portsarim.PiratesTreasureHector
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class QuestScalarStateTest {
    @Test
    fun `every original persistent key imports into its real content field and survives reload`() {
        val f = Fixture()
        assertEquals(13, legacyCases.size)
        assertEquals(legacyCases.map { it.name }.toSet(), f.fields.keys)
        for (case in legacyCases) {
            for (value in case.values()) {
                val player = Player()
                val preserved = mapOf<String, Any>("unrelated" to 9, "quest_stages" to mapOf("quest_lostcity" to 3))
                player.attr.putAllFromPersistence(preserved + (case.legacy to value))
                val field = f.fields.getValue(case.name)
                field.assertResolves()
                assertEquals(value, field.get(player), case.legacy)
                assertEquals(1, player.vars[case.marker])
                assertEquals(preserved, player.attr.toPersistentMap())
                val restored = reload(player)
                assertEquals(value, field.get(restored), case.name)
                assertEquals(preserved, restored.attr.toPersistentMap())
            }
        }
    }

    @Test
    fun `explicit packed zero and current positive values independently beat legacy saves`() {
        val f = Fixture()
        for (case in legacyCases) {
            val field = f.fields.getValue(case.name)
            for ((encoded, expected) in listOf(0 to case.zero(), 1 to case.one())) {
                val player = Player()
                VarPlayerIntMapSetter.set(player, case.varp, encoded)
                player.attr.putAllFromPersistence(mapOf(case.legacy to if (encoded == 0) case.one() else case.zero()))
                assertEquals(expected, field.get(player), case.name)
                assertFalse(player.attr.toPersistentMap().containsKey(case.legacy))
            }
        }
    }

    @Test
    fun `clear before import and repeated stale legacy loads never revive progress`() {
        val f = Fixture()
        for (case in legacyCases) {
            val player = Player()
            val field = f.fields.getValue(case.name)
            player.attr.putAllFromPersistence(mapOf(case.legacy to case.one()))
            field.clear(player)
            val restored = reload(player)
            repeat(2) {
                restored.attr.putAllFromPersistence(mapOf(case.legacy to case.one()))
                field.migrate(restored)
                assertEquals(case.zero(), field.get(restored), case.name)
                assertEquals(0, restored.vars[case.varp])
                assertEquals(1, restored.vars[case.marker])
                assertFalse(restored.attr.toPersistentMap().containsKey(case.legacy))
            }
        }
    }

    @Test
    fun `mixed migrations are independent and malformed scalar values do not crash login`() {
        val f = Fixture()
        val player = Player()
        for ((index, case) in legacyCases.withIndex()) {
            player.attr.putAllFromPersistence(mapOf(case.legacy to case.one()))
            if (index % 2 == 0) f.fields.getValue(case.name).clear(player)
        }
        for ((index, case) in legacyCases.withIndex()) {
            assertEquals(if (index % 2 == 0) case.zero() else case.one(), f.fields.getValue(case.name).get(player))
        }
        for (case in legacyCases) {
            val malformed = Player().apply { attr.putAllFromPersistence(mapOf(case.legacy to "invalid")) }
            assertEquals(case.zero(), f.fields.getValue(case.name).get(malformed))
            assertEquals(1, malformed.vars[case.marker])
            assertTrue(malformed.attr.toPersistentMap().isEmpty())
        }
    }

    @Test
    fun `fresh defaults stay false or zero while arbitrary signed banana values are preserved`() {
        val f = Fixture()
        for (case in legacyCases) assertEquals(case.zero(), f.fields.getValue(case.name).get(Player()))
        val field = PiratesTreasureState.CRATE_BANANAS
        for (count in listOf(-1, 0, 1, 9, 10, 11, Int.MAX_VALUE)) {
            val player = Player().apply { attr.putAllFromPersistence(mapOf("pirates_treasure_crate_bananas" to count)) }
            assertEquals(count, field.get(player))
            assertEquals(count, PiratesTreasureState.crateBananas(reload(player)))
        }
    }

    @Test
    fun `Ernest login migrates journal and pending completion before scheduling the existing queue`() {
        val f = Fixture()
        val events = EventBus()
        with(f.ernest) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        val player = player()
        player.attr.putAllFromPersistence(mapOf(
            "quest.quest_ernestthechicken.FOUND_ERNEST" to true,
            "quest.quest_ernestthechicken.PENDING_COMPLETION" to true,
        ))
        f.ernest.quest.jumpToStage(player, 1)
        assertTrue(events.publish(SessionStateEvent.Login(player)))
        assertEquals(1, player.vars["varp.ernest_found"])
        assertEquals(1, player.vars["varp.ernest_completion_pending"])
        assertEquals(1, player.queueList.count("queue.ernest_complete"))
        assertEquals(1, f.ernest.quest.getQuestStage(player))
        val journal = f.ernest.questLog(access(player))
        assertTrue(journal.contains("I found Ernest. He is a chicken."))
        assertTrue(journal.contains("Professor Oddenstein"))
        assertTrue(journal.contains("pressure gauge"))
        assertTrue(journal.contains("rubber tube"))
        assertTrue(journal.contains("oil can"))
        val resumed = reload(player)
        assertTrue(events.publish(SessionStateEvent.Login(resumed)))
        assertEquals(1, resumed.vars["varp.ernest_completion_pending"])
        assertEquals(1, resumed.queueList.count("queue.ernest_complete"))
        assertEquals(journal, f.ernest.questLog(access(resumed)))
    }

    @Test
    fun `quest reset clears registered fields and completion markers without changing other quest progress`() {
        val f = Fixture()
        val player = player()
        val scripts = listOf(f.ernest, f.runes, f.lostCity, f.blackKnights)
        for (script in scripts) script.quest.jumpToStage(player, 1)
        for (case in legacyCases) player.attr.putAllFromPersistence(mapOf(case.legacy to case.one()))
        f.lostCity.spiritDefeated.set(player, true)
        f.lostCity.searchedByMonks.set(player, true)
        for (script in scripts) script.quest.resetQuest(player)
        val saved = reload(player)
        for (case in legacyCases.take(5)) {
            saved.attr.putAllFromPersistence(mapOf(case.legacy to true))
            assertEquals(false, f.fields.getValue(case.name).get(saved))
        }
        assertFalse(f.lostCity.spiritDefeated.get(player))
        assertFalse(f.lostCity.searchedByMonks.get(player))
        for (script in scripts) assertEquals(0, script.quest.getQuestStage(saved))
        assertTrue(PiratesTreasureState.isLuthasEmployee(saved))
        assertTrue(PiratesTreasureState.isWydinEmployee(saved))
    }

    @Test
    fun `Lost City session flags survive death attribute cleanup but disappear on reconnect`() {
        val quest = LostCityQuest()
        val player = Player()
        quest.thankedWarrior.set(player, true)
        quest.spiritDefeated.set(player, true)
        quest.searchedByMonks.set(player, true)
        player.attr.removeIf { it.resetOnDeath }
        assertTrue(quest.spiritDefeated.get(player))
        assertTrue(quest.searchedByMonks.get(player))
        val restored = reload(player)
        assertTrue(quest.thankedWarrior.get(restored))
        assertFalse(quest.spiritDefeated.get(restored))
        assertFalse(quest.searchedByMonks.get(restored))
        assertFalse(player.attr.toPersistentMap().keys.any { "SPIRIT_DEFEATED" in it || "SEARCHED_BY_MONKS" in it })
    }

    @Test
    fun `temporary coordinate fields distinguish absent from packed zero and do not persist`() {
        val fields = listOf(
            declared(VampyreSlayerCrypt::class.java, null, "COFFIN_COORD"),
            declared(TheRestlessGhostScenery::class.java, null, "GHOST_TILE"),
        )
        for (field in fields) {
            val player = Player()
            field.assertResolves()
            assertNull(field.getOrNull(player))
            field.set(player, 0)
            assertEquals(0, field.getOrNull(player))
            assertNull(field.getOrNull(reload(player)))
        }
    }

    @Test
    fun `temporary integer sentinels and cancelled changes retain the existing value until reset`() {
        val quest = Quest(
            0, "quest_timing_test", 0, "Test", null, null, 1, 0,
            "varp.haunted", rewards {}, null,
        )
        val field = quest.temporaryInt("MIRROR_BLOCKER_TILE", "varp.misthalin_mirror_blocker_tile", -1)
        val player = Player()
        assertEquals(-1, field.get(player))
        val coroutine = player.launch {
            field.set(player, 12345)
            pause { false }
        }
        coroutine.cancel()
        assertEquals(12345, field.get(player))
        quest.resetQuest(player)
        assertEquals(-1, field.get(player))
        assertEquals(-1, field.get(reload(player)))
    }

    @Test
    fun `all permanent migration pairs resolve and remain independent server only values`() {
        val ids = mutableSetOf<Int>()
        for (case in legacyCases) {
            for (symbol in listOf(case.varp, case.marker)) {
                val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
                assertTrue(ids.add(type.id), symbol)
                assertEquals(VarpLifetime.Perm, type.scope, symbol)
                assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
            }
        }
    }

    private class Fixture {
        val ernest = ErnestTheChicken(unused(), unused(), CollisionFlagMap())
        val runes = RuneMysteriesQuest()
        val lostCity = LostCityQuest()
        val blackKnights = BlackKnightsFortress(unused(), unused(), unused(), unused())
        val fields = mapOf(
            "ernest_found" to declared(ernest.javaClass, ernest, "FOUND_ERNEST"),
            "ernest_completion_pending" to declared(ernest.javaClass, ernest, "pendingCompletion"),
            "rune_mysteries_doubted_identity" to declared(runes.javaClass, runes, "doubtedIdentity"),
            "lost_city_thanked_warrior" to declared(lostCity.javaClass, lostCity, "thankedWarrior"),
            "black_knights_meeting_warned" to declared(blackKnights.javaClass, blackKnights, "meetingWarned"),
            "prince_ali_met_ned" to declared(PrinceAliRescueDisguise::class.java, null, "MET_NED"),
            "prince_ali_met_leela" to declared(PrinceAliRescueJail::class.java, null, "MET_LEELA"),
            "prince_ali_asked_keli" to declared(PrinceAliRescueJail::class.java, null, "ASKED_TO_JOIN_KELI"),
            "pirates_treasure_luthas_employed" to declared(PiratesTreasureState::class.java, null, "LUTHAS_EMPLOYED"),
            "pirates_treasure_crate_bananas" to declared(PiratesTreasureState::class.java, null, "CRATE_BANANAS"),
            "pirates_treasure_crate_rum" to declared(PiratesTreasureState::class.java, null, "CRATE_RUM"),
            "pirates_treasure_wydin_employed" to declared(PiratesTreasureState::class.java, null, "WYDIN_EMPLOYED"),
            "pirates_treasure_dug_once" to declared(PiratesTreasureHector::class.java, null, "DUG_ONCE"),
        )
    }

    private data class LegacyCase(val name: String, val legacy: String, val kind: String, val varp: String, val marker: String) {
        fun values(): List<Any> = if (kind == "integer") (0..10).toList() else listOf(false, true)
        fun zero(): Any = if (kind == "integer") 0 else false
        fun one(): Any = if (kind == "integer") 1 else true
    }

    companion object {
        private val legacyCases by lazy {
            checkNotNull(QuestScalarStateTest::class.java.getResourceAsStream("/quest-state/legacy-scalar-fields.tsv"))
                .bufferedReader().useLines { lines ->
                    lines.filter(String::isNotBlank).map { line ->
                        val columns = line.split('\t')
                        LegacyCase(columns[0], columns[1], columns[2], columns[3], columns[4])
                    }.toList()
                }
        }

        @Suppress("UNCHECKED_CAST")
        private fun declared(type: Class<*>, owner: Any?, name: String): QuestVar<Any> =
            type.getDeclaredField(name).apply { isAccessible = true }.get(owner) as QuestVar<Any>

        private fun player() = Player().apply {
            inv = invMap.getOrPut("inv.inv")
            invMap.getOrPut("inv.bank")
        }

        private fun reload(original: Player) = player().apply {
            vars.backing.putAll(original.vars.backing.filterKeys { ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm })
            attr.putAllFromPersistence(original.attr.toPersistentMap())
        }

        private fun access(player: Player) = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
