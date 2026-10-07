package org.rsmod.content.quest.area.portsarim.pandemonium

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow

@ResourceLock("ServerCacheManager")
class PandemoniumCacheTest {
    @Test
    fun `completion uses the quest stage without overwriting companion flags`() {
        val row = QuestRow.getRow("dbrow.quest_pandemonium")
        val stage = checkNotNull(ServerCacheManager.getVarbit("varbit.sailing_intro".asRSCM()))
        val reached = checkNotNull(ServerCacheManager.getVarbit("varbit.sailing_intro_reached_wreck".asRSCM()))
        val kits = checkNotNull(ServerCacheManager.getVarbit("varbit.sailing_intro_received_repairkits".asRSCM()))
        assertEquals("varp.sailing_intro_primary".asRSCM(), stage.varp)
        assertEquals(stage.varp, reached.varp)
        assertEquals(stage.varp, kits.varp)
        assertEquals(0, stage.startBit)
        assertEquals(5, stage.endBit)
        assertEquals(6, reached.startBit)
        assertEquals(7, kits.startBit)
        assertEquals(50, row.endstate)
        assertEquals(row.endstate, 242 and 63)
        assertEquals(1, row.questpoints)
        val reward = row.statXpAwarded.single()
        assertEquals("stat.sailing".asRSCM(), reward.t0.id)
        assertEquals(3000, reward.t1)
    }

    @Test
    fun `tutorial source zone retains its native hull and wreck destination`() {
        val hull = placement("loc.sailing_boat_hull_kandarin_2x5_wood", 3843, 6401, 0)
        assertEquals(10, hull.shape)
        assertEquals(0, hull.angle)
        val wreck = placement("loc.sailing_intro_wreck", 3031, 3035, 0)
        assertEquals(10, wreck.shape)
        assertEquals(1, wreck.angle)
    }

    @Test
    fun `borrowed boat fittings expose native navigation and salvage operations`() {
        val helm = checkNotNull(ServerCacheManager.getObject("loc.sailing_intro_helm_not_in_use".asRSCM()))
        val hook = checkNotNull(ServerCacheManager.getObject("loc.sailing_intro_salvaging_hook_ops".asRSCM()))
        assertEquals("Navigate", helm.actions.getOpOrNull(0))
        assertEquals("Deploy", hook.actions.getOpOrNull(0))
        for (symbol in listOf("loc.sailing_intro_navigating", "loc.sailing_intro_salvaging_hook")) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            assertTrue(type.transforms?.isNotEmpty() == true, symbol)
        }
    }

    @Test
    fun `salvage and betrayal animations have packed playback durations`() {
        for (symbol in listOf(
            "seq.sailing_boats_salvage_hook_kandarin_2x5_drop01",
            "seq.sailing_boats_salvage_hook_kandarin_2x5_reset01",
            "seq.sailing_intro_human_overboard",
            "seq.sailing_intro_anne_push",
        )) {
            val sequence = checkNotNull(ServerCacheManager.getAnim(symbol.asRSCM()))
            assertTrue(sequence.tickDuration > 0, symbol)
        }
    }

    private fun placement(symbol: String, x: Int, z: Int, level: Int): MapLocDefinition {
        val cache = ServerCacheManager.init(241)
        try {
            val group = ((x shr 6) shl 8) or (z shr 6)
            val bytes = checkNotNull(cache.data(MAPS, group, 1))
            return MapLocListDecoder.decode(InlineByteBuf(bytes)).spawns.toLongArray()
                .map(::MapLocDefinition)
                .single { it.id == symbol.asRSCM() && it.localX == (x and 63) &&
                    it.localZ == (z and 63) && it.level == level }
        } finally {
            cache.close()
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(241).close()
        }
    }
}
