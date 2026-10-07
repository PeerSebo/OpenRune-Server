package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.game.loc.LocEntity
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class ARuffSituationCacheTest {
    @Test
    fun `native quest row supports the captured den stage`() {
        val row = QuestRow.getRow("dbrow.quest_ruffsituation".asRSCM())
        assertTrue(row.endstate > ARuffSituation.FOUND_CLUES)
    }

    @Test
    fun `quest npcs expose interactions used by the rescue route`() {
        val expected = mapOf(
            "dogq_talia" to "Talk-to",
            "dogq_chase" to "Talk-to",
            "dogq_chase_post" to "Talk-to",
            "dogq_dog_stray" to "Interact",
            "dogq_goblin_picklenose" to "Talk-to",
            "dogq_goblin_toetaller" to "Talk-to",
            "dogq_puppy_three" to "Pick-up",
        )
        for ((name, option) in expected) {
            val npc = checkNotNull(ServerCacheManager.getNpc("npc.$name".asRSCM(RSCMType.NPC)))
            assertEquals(option, npc.actions.getOpOrNull(0), name)
        }
    }

    @Test
    fun `captured shelter and rescue npcs are placed on the server map`() {
        val cache = ServerCacheManager.init(241)
        try {
            fun hasSpawn(mapX: Int, mapZ: Int, npc: String): Boolean {
                val group = (mapX shl 8) or mapZ
                val bytes = cache.data(MAPS, group, 5) ?: return false
                val id = "npc.$npc".asRSCM(RSCMType.NPC)
                return MapNpcListDecoder.decode(InlineByteBuf(bytes)).packedSpawns
                    .toIntArray().map(::MapNpcDefinition).any { it.id == id }
            }
            assertTrue(hasSpawn(47, 54, "dogq_talia"))
            assertTrue(hasSpawn(47, 54, "dogq_chase_multi"))
            assertTrue(hasSpawn(49, 53, "dogq_varrock_stray_dog_multi_quest"))
            assertTrue(hasSpawn(48, 53, "dogq_goblin_picklenose_multi"))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `the three den clues exist in the native map`() {
        val cache = ServerCacheManager.init(241)
        try {
            val group = (49 shl 8) or 53
            val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
            val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
            val builder = GameMapBuilder()
            GameMapDecoder.putLocs(builder, CollisionFlagMap(), MapSquareKey(49, 53), tiles, locs)
            val ids = builder.zoneBuilders.values.flatMap { zone ->
                zone.build().byte2IntEntrySet().map { LocEntity(it.intValue).id }
            }.toSet()
            for (clue in listOf("dogq_den_box_multi", "dogq_den_scraps_multi", "dogq_den_bed_multi")) {
                assertTrue("loc.$clue".asRSCM(RSCMType.LOC) in ids, clue)
            }
        } finally {
            cache.close()
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
