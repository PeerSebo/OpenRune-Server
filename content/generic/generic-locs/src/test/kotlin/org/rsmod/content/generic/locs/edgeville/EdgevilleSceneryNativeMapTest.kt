package org.rsmod.content.generic.locs.edgeville

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.definition.codec.ObjectCodec
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
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.loc.*
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgevilleSceneryNativeMapTest {
    private val collision = CollisionFlagMap()
    private val locs = mutableListOf<BoundLocInfo>()
    private val leverModels = mutableMapOf<Int, List<Int>?>()
    private val arrivals = listOf(
        Triple("loc.edgeville_wildy_lever", EdgevilleSceneryScript.EdgevilleLever, EdgevilleSceneryScript.EdgevilleLanding),
        Triple("loc.wildinlever", EdgevilleSceneryScript.ArdougneLever, EdgevilleSceneryScript.ArdougneLanding),
        Triple("loc.wildoutlever", EdgevilleSceneryScript.KeepLever, EdgevilleSceneryScript.KeepLanding),
    )

    @BeforeAll fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            for (id in listOf(1814, 9472, 9738, 26761, 46438, 5961)) {
                leverModels[id] = ObjectCodec(240).loadData(id, checkNotNull(cache.data(2, 6, id))).objectModels
            }
            val builder = GameMapBuilder()
            val points = arrivals.map { it.second } + CoordGrid(3268, 3332)
            for ((mx, mz) in points.map { it.mx to it.mz }.toSet()) {
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8)
                    for (z in mz * 64 until (mz + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(collision, MapSquareKey(mx, mz), tiles)
                GameMapDecoder.putLocs(builder, collision, MapSquareKey(mx, mz), tiles, spawns)
            }
            for ((packed, zone) in builder.zoneBuilders) {
                val base = ZoneKey(packed).toCoords()
                for (entry in zone.build().byte2IntEntrySet()) {
                    val key = LocZoneKey(entry.byteKey)
                    val entity = LocEntity(entry.intValue)
                    val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                    locs += BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id)))
                }
            }
        } finally { cache.close() }
    }

    @Test fun `lever arrivals are open native tiles and can reach their local lever`() {
        for ((symbol, source, destination) in arrivals) {
            val loc = locs.single { it.id == symbol.asRSCM() && it.coords == source }
            val blocked = collision[destination.x, destination.z, destination.level] and
                (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR)
            assertEquals(0, blocked, "Arrival in solid scenery: $symbol at $destination")
            val route = RouteFinding(collision).findRoute(level = destination.level,
                srcX = destination.x, srcZ = destination.z, destX = loc.x, destZ = loc.z,
                destWidth = loc.width, destLength = loc.length, locShape = loc.shapeId,
                locAngle = loc.angleId, blockAccessFlags = loc.forceApproachFlags, moveNear = false)
            assertTrue(route.success, "No native click route from $destination to $symbol")
        }
    }

    @Test fun `teleport levers use the same static up model and a distinct native down model`() {
        for (id in listOf(1814, 9472, 9738, 26761, 46438)) assertEquals(listOf(2108), leverModels[id])
        assertEquals(listOf(2077), leverModels["loc.magearena_lever_down".asRSCM()])
    }

    @Test fun `all three Edgeville coffins and southern Varrock signpost exist on native ground plane`() {
        for (coords in EdgevilleSceneryScript.Coffins) {
            assertNotNull(locs.singleOrNull { it.id == "loc.coffin".asRSCM() && it.coords == coords })
        }
        assertNotNull(locs.singleOrNull {
            it.id == "loc.fai_varrock_signpost_1".asRSCM() && it.coords == CoordGrid(3268, 3332)
        })
    }
}
