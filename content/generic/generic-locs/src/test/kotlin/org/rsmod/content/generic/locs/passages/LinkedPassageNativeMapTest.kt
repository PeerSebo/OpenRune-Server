package org.rsmod.content.generic.locs.passages

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
import org.rsmod.game.loc.*
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkedPassageNativeMapTest {
    private val collision = CollisionFlagMap()
    private val locs = mutableListOf<BoundLocInfo>()
    private val ends = LinkedPassages.all.flatMap { listOf(it.a, it.b) }

    @BeforeAll fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            for ((mx, mz) in ends.map { it.coords.mx to it.coords.mz }.toSet()) {
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

    @Test fun `both Wilderness Slayer entrances land on open floor with a route back out`() {
        for (end in ends) {
            val target = locs.singleOrNull { it.id == end.loc.asRSCM() && it.coords == end.coords }
            assertNotNull(target, "Native passage missing: $end")
            val loc = checkNotNull(target)
            val landing = end.landing ?: Passages.landingCandidates(end.coords).firstOrNull { !blocked(it) }
            assertNotNull(landing, "No landing: $end")
            val from = checkNotNull(landing)
            assertFalse(blocked(from), "Blocked landing for $end")
            val route = RouteFinding(collision).findRoute(level = from.level, srcX = from.x, srcZ = from.z,
                destX = loc.x, destZ = loc.z, destWidth = loc.width, destLength = loc.length,
                locShape = loc.shapeId, locAngle = loc.angleId,
                blockAccessFlags = loc.forceApproachFlags, moveNear = false)
            assertTrue(route.success, "No return click route from $from to $end")
        }
    }

    private fun blocked(tile: CoordGrid): Boolean = collision[tile.x, tile.z, tile.level] and
        (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) != 0
}
