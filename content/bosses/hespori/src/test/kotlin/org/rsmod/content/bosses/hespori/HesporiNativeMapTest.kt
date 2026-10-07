package org.rsmod.content.bosses.hespori

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.route.RayCastValidator
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HesporiNativeMapTest {
    private val flags = CollisionFlagMap()

    @BeforeAll fun maps() {
        val cache = ServerCacheManager.init(241)
        try {
            for ((mx, mz) in listOf(19 to 157, 19 to 58)) {
                val id = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, id, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, id, 1))))
                for (l in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8) for (z in mz * 64 until (mz + 1) * 64 step 8) flags.allocateIfAbsent(x, z, l)
                val square = MapSquareKey(mx, mz)
                GameMapDecoder.putMaps(flags, square, tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), flags, square, tiles, locs)
            }
        } finally { cache.close() }
    }

    @Test fun `both player landings stand on native open ground`() {
        val sites = listOf(HesporiGarden.EXIT, HesporiGarden.ARRIVAL)
        val bad = sites.filter { blocked(it) }
        val alternatives = bad.associateWith { tile -> buildList {
            for (dx in -3..3) for (dz in -3..3) if (!blocked(tile.translate(dx, dz))) add(tile.translate(dx, dz))
        } }
        assertTrue(bad.isEmpty(), "Blocked native sites: $bad, nearby candidates: $alternatives")
    }

    @Test fun `the four rooted flowers can be attacked from native open ground beside each plant`() {
        val ray = RayCastValidator(flags)
        for (flower in HesporiGarden.FLOWERS) {
            val neighbours = listOf(flower.translate(-1, 0), flower.translate(1, 0), flower.translate(0, -1), flower.translate(0, 1))
            assertTrue(neighbours.any { !blocked(it) && ray.hasLineOfSight(it, flower) }, "Unreachable flower $flower")
        }
    }

    @Test fun `patch states match native dedicated var and outside sprout bloom`() {
        val patch = ServerCacheManager.getObject(HesporiGarden.PARENT.asRSCM())!!
        assertEquals(HesporiCrop.TRANSMIT.asRSCM(), patch.multiVarBit)
        assertEquals((33722..33730).toList(), patch.transforms!!.take(9))
        val sprout = ServerCacheManager.getObject("loc.kebos_farming_guild_hespori_indicator".asRSCM())!!
        assertEquals(patch.multiVarBit, sprout.multiVarBit)
        assertEquals("loc.kebos_farming_guild_hespori_grown".asRSCM(), sprout.transforms!![7])
        assertEquals("loc.kebos_farming_guild_hespori_notgrown".asRSCM(), sprout.transforms!![6])
    }

    private fun blocked(tile: CoordGrid): Boolean = flags[tile.x, tile.z, tile.level] and
        (CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.LOC) != 0
}
