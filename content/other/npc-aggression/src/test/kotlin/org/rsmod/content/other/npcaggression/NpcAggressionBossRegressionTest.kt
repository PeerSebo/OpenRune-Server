package org.rsmod.content.other.npcaggression

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.hunt.HuntCheckNotTooStrong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class NpcAggressionBossRegressionTest {
    @Test
    fun `ordinary monsters spawn with the combat level and wilderness rule`() {
        val melee = listOf("icegiant", "icewarrior", "giant", "mossgiant", "hobgoblin_unarmed",
            "skeleton_unarmed", "ghost", "whitewolf", "dwarf_chaos", "slayer_banshee_1",
            "aggressive_black_knight", "bat", "deadly_red_spider", "chaos_temple_monk1")
        val ranged = listOf("bearded_dark_wizard", "young_dark_wizard", "fai_dark_wizard_old_1",
            "qip_ds_young_dark_wizard1")
        for ((names, expectedMode) in listOf(melee to 1, ranged to 2)) {
            for (name in names) {
                val npc = Npc("npc.$name", CoordGrid(3200, 3200))
                assertEquals(expectedMode, npc.huntMode, name)
                val mode = checkNotNull(ServerCacheManager.getHunt(expectedMode))
                assertEquals(HuntCheckNotTooStrong.OutsideWilderness, mode.checkNotTooStrong, name)
            }
        }
    }

    @Test
    fun `bosses and always aggressive stronghold monsters keep their own hunt rule`() {
        for (name in listOf("cowboss", "scorpia_minion", "sos_fam_fleshcrawler", "sos_pest_giantspider1")) {
            val npc = Npc("npc.$name", CoordGrid(3200, 3200))
            val mode = checkNotNull(ServerCacheManager.getHunt(checkNotNull(npc.huntMode)))
            assertEquals(HuntCheckNotTooStrong.Off, mode.checkNotTooStrong, name)
        }
    }

    @Test
    fun `generated aggression cannot start a separate generic Hespori fight`() {
        val boss = checkNotNull(ServerCacheManager.getNpc("npc.hespori".asRSCM()))
        val original = boss.huntMode
        try {
            boss.huntMode = null
            val applied = NpcAggressionScript().apply(listOf(NpcAggressionEntry(id = "npc.hespori")), force = false)
            assertEquals(0, applied)
            assertNull(boss.huntMode)
        } finally {
            boss.huntMode = original
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
