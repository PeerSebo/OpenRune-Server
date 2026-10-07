package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.hunter.configs.HunterRumourPrey
import org.rsmod.content.skills.hunter.configs.RumourMaster
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class HunterGuildVarsTest {
    @Test
    fun `saved assignments progress unlocks and pending rerolls migrate without losing sack rewards`() {
        val player = Player()
        val retained = mapOf("hunter_sack_pending_expert" to "obj.logs,10,true", "unrelated" to 7)
        val saved = mutableMapOf<String, Any>(
            "hunter_rumours_unlocked" to true,
            "hunter_rumours_back_to_back" to true,
            "hunter_rumours_reset_lock" to true,
            "hunter_basic_blueprint_claimed" to true,
            "hunter_perfected_whistle_crafted" to true,
            "hunter_infinite_whistle_unlocked" to true,
            "hunter_enhanced_blueprint_seen" to true,
            "hunter_perfected_blueprint_seen" to true,
            "hunter_rumours_completed" to 321,
            "hunter_quetzal_feed_credit" to 9876,
            "hunter_rumours_active" to "TECO",
            "hunter_rumours_reroll_after" to "WOLF",
            "hunter_rumours_last" to "HERBIBOAR",
        )
        for (master in RumourMaster.entries) {
            saved["hunter_rumour_${master.saveName}_prey"] = "DASHING"
            saved["hunter_rumour_${master.saveName}_count"] = 23
            saved["hunter_rumour_${master.saveName}_original"] = "RED_CHINCHOMPA"
            saved["hunter_rumour_${master.saveName}_original_count"] = 49
        }
        player.attr.putAllFromPersistence(saved + retained)

        player.migrateHunterGuildState()

        assertTrue(player.rumoursUnlocked)
        assertTrue(player.rumoursBackToBack)
        assertTrue(player.rumoursResetLock)
        assertTrue(player.basicBlueprintClaimed)
        assertTrue(player.perfectedWhistleCrafted)
        assertTrue(player.infiniteWhistleUnlocked)
        assertTrue(player.enhancedBlueprintSeen)
        assertTrue(player.perfectedBlueprintSeen)
        assertEquals(321, HunterRumours.completed(player))
        assertEquals(9876, QuetzalWhistleService.feedCredit(player))
        assertEquals(RumourMaster.TECO, HunterRumours.activeMaster(player))
        assertEquals(RumourMaster.WOLF.saveCode, player.rumourRerollMaster)
        assertEquals(HunterRumourPrey.HERBIBOAR.saveCode, player.rumourLastPrey)
        for (master in RumourMaster.entries) {
            assertEquals(HunterRumourPrey.DASHING, HunterRumours.assigned(player, master))
            assertEquals(23, HunterRumours.count(player, master))
            assertEquals(HunterRumourPrey.RED_CHINCHOMPA, player.rumourPrey(master, saved = true))
            assertEquals(49, player.rumourCatchCount(master, saved = true))
        }
        assertEquals(retained, player.attr.toPersistentMap())
    }

    @Test
    fun `reconnecting cannot import stale attributes over cleared or advanced progress`() {
        val original = Player()
        original.attr.putAllFromPersistence(mapOf("hunter_rumours_completed" to 10, "hunter_rumours_back_to_back" to true))
        original.migrateHunterGuildState()
        original.rumoursCompleted = 12
        original.rumoursBackToBack = false
        val reconnected = Player()
        reconnected.vars.backing.putAll(original.vars.backing)
        reconnected.attr.putAllFromPersistence(mapOf("hunter_rumours_completed" to 10, "hunter_rumours_back_to_back" to true))

        reconnected.migrateHunterGuildState()

        assertEquals(12, reconnected.rumoursCompleted)
        assertFalse(reconnected.rumoursBackToBack)
        assertTrue(reconnected.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `invalid legacy assignments and out of bounds counters become valid defaults`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(
            "hunter_rumours_active" to "REMOVED_MASTER",
            "hunter_rumours_last" to "REMOVED_PREY",
            "hunter_rumour_gilman_prey" to "REMOVED_PREY",
            "hunter_rumour_gilman_count" to -15,
            "hunter_rumours_completed" to -3,
            "hunter_quetzal_feed_credit" to 15000,
        ))
        player.migrateHunterGuildState()
        assertNull(HunterRumours.activeMaster(player))
        assertNull(HunterRumours.assigned(player, RumourMaster.GILMAN))
        assertEquals(0, HunterRumours.count(player, RumourMaster.GILMAN))
        assertEquals(0, player.rumoursCompleted)
        assertEquals(10000, QuetzalWhistleService.feedCredit(player))
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `explicit empty assignments balances and flags win before migration marker exists`() {
        val player = Player()
        player.rumoursBackToBack = false
        player.quetzalFeedCredit = 0
        player.rumourActiveMaster = 0
        player.attr.putAllFromPersistence(mapOf(
            "hunter_rumours_back_to_back" to true,
            "hunter_quetzal_feed_credit" to 100,
            "hunter_rumours_active" to "TECO",
            "hunter_rumours_completed" to 42,
        ))

        player.migrateHunterGuildState()

        assertFalse(player.rumoursBackToBack)
        assertEquals(0, player.quetzalFeedCredit)
        assertNull(HunterRumours.activeMaster(player))
        assertEquals(42, player.rumoursCompleted)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `guild state is permanent server only and every persisted enum code remains stable`() {
        val symbols = listOf("hunter_guild_flags", "hunter_rumours_completed", "hunter_rumours_active",
            "hunter_rumours_last", "hunter_rumours_reroll_after", "hunter_quetzal_feed_credit") +
            RumourMaster.entries.flatMap { master -> listOf("prey", "count", "original", "original_count").map {
                "hunter_rumour_${master.saveName}_$it"
            } }
        for (symbol in symbols) {
            val type = checkNotNull(ServerCacheManager.getVarp("varp.$symbol".asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, type.transmit, symbol)
        }
        val masters = "GILMAN,CERVUS,ORNUS,ACO,TECO,WOLF".split(',')
        val prey = ("WAGTAIL,WILD,SAPPHIRE,SWAMP,LARUPIA,BARBED,SNOWY,PRICKLY,JERBOA,GRAAHK," +
            "SPOTTED,WARLOCK,ORANGE,RAZOR,SABRE,CHINCHOMPA,KYATT,DARK,FOX,RED,RED_CHINCHOMPA," +
            "DASHING,SUN_ANTELOPE,SUN_MOTH,TECU,HERBIBOAR,MOON_MOTH,MOON_ANTELOPE").split(',')
        for ((code, name) in masters.withIndex()) assertEquals(code + 1, RumourMaster.valueOf(name).saveCode)
        for ((code, name) in prey.withIndex()) assertEquals(code + 1, HunterRumourPrey.valueOf(name).saveCode)
        assertEquals(RumourMaster.entries.size, RumourMaster.entries.map { it.saveCode }.toSet().size)
        assertEquals(HunterRumourPrey.entries.size, HunterRumourPrey.entries.map { it.saveCode }.toSet().size)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
