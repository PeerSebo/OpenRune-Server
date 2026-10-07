package org.rsmod.api.bossbar.plugin

import dev.openrune.ServerCacheManager
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.params

@ResourceLock("ServerCacheManager")
class BossPackedConfigurationTest {
    @Test
    fun `godwars routing and faction tags coexist with kill counters and weaknesses`() {
        for (faction in listOf("bandos", "saradomin", "zamorak")) {
            val boss = npc("godwars_${faction}_avatar")
            assertEquals(6, boss.huntMode)
            assertEquals(30, boss.huntRange)
            assertEquals(30, boss.maxRange)
            assertEquals(NpcMode.Wander, boss.defaultMode)
            assertEquals(1, boss.paramOrNull(params.boss_hp_bar_mode))
        }
        assertEquals(40, npc("godwars_bandos_avatar").paramOrNull(params.elemental_weakness_percent))
        assertEquals(30, npc("godwars_zamorak_avatar").paramOrNull(params.elemental_weakness_percent))
        for (faction in listOf("bandos", "armadyl", "saradomin", "zamorak")) {
            for (role in listOf("mage", "ranger", "warrior")) {
                val follower = npc("godwars_spiritual_${faction}_$role")
                assertTrue(follower.isCategoryType("category.godwars_${faction}_follower"))
                assertEquals("varp.kc_spiritual_$role".asRSCM(), follower.paramOrNull(params.killcount_varp)?.id)
                assertEquals(false, follower.paramOrNull(params.killcount_notify))
            }
        }
    }

    @Test
    fun `barrows combat definitions retain their elemental weaknesses`() {
        for (symbol in listOf("ahrim", "dharok", "guthan", "karil", "torag", "verac", "spider", "giantspider")) {
            val type = npc("barrows_$symbol")
            assertEquals(if (symbol == "giantspider") 40 else 50,
                type.paramOrNull(params.elemental_weakness_percent), symbol)
            assertEquals(if (symbol.contains("spider")) 3 else 0,
                type.paramOrNull(params.elemental_weakness_type), symbol)
            assertNotNull(type.paramOrNull(params.attack_anim), symbol)
        }
    }

    @Test
    fun `boss metadata coexists with elemental weaknesses and intended combat routing`() {
        for ((symbol, percent, element) in listOf(
            Triple("hillgiant_boss", 20, 2), Triple("gb_mossgiant", 50, 3),
            Triple("hespori", 100, 3), Triple("slayer_kraken_boss", 50, 2),
            Triple("sarachnis", 40, 3), Triple("cata_boss", 40, 1),
            Triple("smoke_devil_boss", 20, 0),
        )) {
            val npc = npc(symbol)
            assertEquals(1, npc.paramOrNull(params.boss_hp_bar_mode), symbol)
            assertEquals(percent, npc.paramOrNull(params.elemental_weakness_percent), symbol)
            assertEquals(element, npc.paramOrNull(params.elemental_weakness_type), symbol)
        }
        for (symbol in listOf("slayer_kraken_boss", "slayer_kraken_boss_whirlpool",
            "slayer_kraken_boss_tentacle", "slayer_kraken_boss_tentacle_whirlpool")) {
            val npc = npc(symbol)
            assertEquals(MoveRestrict.NoMove, npc.moveRestrict, symbol)
            assertEquals(NpcMode.None, npc.defaultMode, symbol)
            assertEquals(10, npc.attackRange, symbol)
            assertEquals(16, npc.maxRange, symbol)
            assertEquals(15, npc.respawnRate, symbol)
            assertFalse(npc.giveChase, symbol)
            assertEquals(4, npc.paramOrNull(params.attackrate), symbol)
        }
        val tentacle = npc("slayer_kraken_boss_tentacle")
        assertEquals(50, tentacle.paramOrNull(params.elemental_weakness_percent))
        assertEquals(2, tentacle.paramOrNull(params.elemental_weakness_type))
        assertNull(tentacle.paramOrNull(params.boss_hp_bar_mode))
        assertEquals(30, npc("hillgiant_boss").maxRange)
        assertEquals(6, npc("hillgiant_boss").paramOrNull(params.attackrate))
        assertEquals(20, npc("hespori").maxRange)
        assertEquals(MoveRestrict.NoMove, npc("hespori").moveRestrict)
        assertEquals(17, npc("sarachnis").respawnRate)
        assertEquals(16, npc("sarachnis").attackRange)
        assertEquals(MoveRestrict.NoMove, npc("cata_boss").moveRestrict)
        assertEquals(MoveRestrict.PassThru, npc("smoke_devil_boss").moveRestrict)
        assertEquals(2, npc("smoke_devil_boss").paramOrNull(params.attackrate))
    }

    @Test
    fun `migrated stats and helper routing retain their authored values`() {
        for ((symbol, stats) in mapOf(
            "hillgiant_boss" to listOf(120, 90, 100, 60, 120),
            "gb_mossgiant" to listOf(115, 130, 100, 100, null),
            "gb_growthling" to listOf(10, 40, 38, 40, null),
            "sarachnis" to listOf(400, 200, 240, 150, 300),
            "sarachnis_melee_spawn" to listOf(30, 150, 120, 50, 1),
            "sarachnis_mage_spawn" to listOf(30, 10, 10, 50, 1),
            "smoke_devil_boss" to listOf(240, 230, 220, 360, 310),
        )) {
            val npc = npc(symbol)
            val actual = listOf(npc.hitpoints, npc.attack, npc.strength, npc.defence, npc.ranged)
            stats.forEachIndexed { index, expected ->
                if (expected != null) assertEquals(expected, actual[index], "$symbol stat $index")
            }
        }
        assertEquals(1, npc("sarachnis_melee_spawn").attackRange)
        assertEquals(7, npc("sarachnis_mage_spawn").attackRange)
        for (direction in listOf("n", "e", "s", "w")) {
            for (state in listOf("open", "closed")) {
                val npc = npc("cata_boss_altar_${state}_$direction")
                assertEquals(MoveRestrict.NoMove, npc.moveRestrict)
                assertEquals(NpcMode.None, npc.defaultMode)
                assertEquals(0, npc.wanderRange)
                assertFalse(npc.giveChase)
            }
        }
    }

    @Test
    fun `server only boss overrides preserve native model inheritance and movement sequences`() {
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        val server = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val symbols = listOf("hillgiant_boss", "gb_mossgiant", "gb_growthling", "hespori", "hespori_healer_active",
                "slayer_kraken_boss", "slayer_kraken_boss_whirlpool", "slayer_kraken_boss_tentacle",
                "slayer_kraken_boss_tentacle_whirlpool", "sarachnis", "sarachnis_melee_spawn",
                "sarachnis_mage_spawn", "cata_boss", "cata_boss_minion", "cata_boss_ankou", "smoke_devil_boss")
            for (symbol in symbols) {
                val id = "npc.$symbol".asRSCM()
                val native = NPCCodec(240).loadData(id, checkNotNull(live.data(2, 9, id)))
                val packed = NPCCodec(240).loadData(id, checkNotNull(server.data(2, 9, id)))
                assertFalse(native.models.isNullOrEmpty(), symbol)
                assertEquals(native.models, packed.models, symbol)
                assertEquals(native.originalColours, packed.originalColours, symbol)
                assertEquals(native.modifiedColours, packed.modifiedColours, symbol)
                assertEquals(native.widthScale, packed.widthScale, symbol)
                assertEquals(native.heightScale, packed.heightScale, symbol)
                assertEquals(native.standAnim, packed.standAnim, symbol)
                assertEquals(native.walkAnim, packed.walkAnim, symbol)
                assertEquals(native.standAnim, npc(symbol).standAnim, symbol)
                assertEquals(native.walkAnim, npc(symbol).walkAnim, symbol)
            }
        } finally {
            live.close()
            server.close()
        }
    }

    private fun npc(symbol: String) = requireNotNull(ServerCacheManager.getNpc("npc.$symbol".asRSCM()))

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
