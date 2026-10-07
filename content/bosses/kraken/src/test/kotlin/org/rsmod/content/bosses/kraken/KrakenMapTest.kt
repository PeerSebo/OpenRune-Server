package org.rsmod.content.bosses.kraken

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
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.*
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KrakenMapTest {
    private val collision = CollisionFlagMap()
    private val nativeLocs = mutableListOf<BoundLocInfo>()
    private fun load(points: List<CoordGrid>) {
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            for ((mx, mz) in points.map { it.mx to it.mz }.toSet()) {
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8)
                    for (z in mz * 64 until (mz + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(collision, MapSquareKey(mx, mz), tiles)
                GameMapDecoder.putLocs(builder, collision, MapSquareKey(mx, mz), tiles, locs)
            }
            for ((packed, zone) in builder.zoneBuilders) {
                val base = ZoneKey(packed).toCoords()
                for (entry in zone.build().byte2IntEntrySet()) {
                    val key = LocZoneKey(entry.byteKey)
                    val entity = LocEntity(entry.intValue)
                    val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                    nativeLocs += BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id)))
                }
            }
        } finally { cache.close() }
    }
    private fun blocked(tile: CoordGrid) = collision[tile.x, tile.z, tile.level] and
        (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) != 0
    private fun open(tile: CoordGrid) {
        val nearby = buildList { for (x in -3..3) for (z in -3..3) if (!blocked(tile.translate(x, z))) add(tile.translate(x, z)) }
        assertFalse(blocked(tile), "Blocked tile $tile; nearby open: $nearby")
    }
    private fun route(from: CoordGrid, loc: BoundLocInfo) {
        open(from)
        val result = RouteFinding(collision).findRoute(level = from.level, srcX = from.x, srcZ = from.z,
            destX = loc.x, destZ = loc.z, destWidth = loc.width, destLength = loc.length,
            locShape = loc.shapeId, locAngle = loc.angleId, blockAccessFlags = loc.forceApproachFlags, moveNear = false)
        assertTrue(result.success, "No normal click route from $from to $loc")
        val end = result.lastOrNull()?.let { CoordGrid(it.x, it.z, from.level) } ?: from
        assertTrue(BoundValidator(collision).touches(Player().apply { coords = end }.avatar, loc), "Out of op reach: $end to $loc")
    }

    @BeforeAll fun nativeMap() = load(listOf(KrakenCove.CoveArrival, KrakenCove.SurfaceArrival))

    @Test fun `all arrivals are walkable and native passages are reachable by ordinary click`() {
        val paths = listOf(
            KrakenCove.SurfaceArrival to "loc.slayer_cave_kraken_maincave_entrance",
            KrakenCove.CoveArrival to "loc.slayer_cave_kraken_maincave_exit",
            KrakenCove.CoveArrival to "loc.slayer_cave_kraken_boss_entrance",
            KrakenCove.RoomArrival to "loc.slayer_cave_kraken_boss_exit")
        for ((start, symbol) in paths) route(start, nativeLocs.single { it.id == symbol.asRSCM() && it.level == start.level })
        open(KrakenCove.OutsideRoom)
    }

    @Test fun `every native whirlpool has a reachable shore firing position`() {
        val ray = RayCastValidator(collision)
        for ((site, size) in listOf(KrakenCove.BossSpawn to 5) + KrakenCove.Tentacles.map { it to 2 }) {
            val positions = buildList {
                for (x in 2268..2294) for (z in 10022..10047) {
                    val tile = CoordGrid(x, z)
                    if (blocked(tile)) continue
                    val distance = maxOf((site.x - x).coerceAtLeast(0), (x - site.x - size + 1).coerceAtLeast(0),
                        (site.z - z).coerceAtLeast(0), (z - site.z - size + 1).coerceAtLeast(0))
                    if (distance > 8 || !ray.hasLineOfSight(tile, site, destWidth=size, destLength=size)) continue
                    val path = RouteFinding(collision).findRoute(level = 0, srcX = KrakenCove.RoomArrival.x, srcZ = KrakenCove.RoomArrival.z, destX = x, destZ = z, moveNear = false)
                    if (path.success) add(tile)
                }
            }
            assertTrue(positions.isNotEmpty(), "Cannot shoot pool at $site from any reachable shore tile")
        }
    }

    @Test fun `original pools and active forms remain rooted with long range combat routing`() {
        for (symbol in KrakenRules.Types) {
            val npc = Npc(symbol, KrakenCove.BossSpawn)
            assertEquals(10, npc.attackRange, symbol)
            assertEquals(dev.openrune.types.MoveRestrict.NoMove, npc.moveRestrict, symbol)
        }
    }

    @Test fun `visible transformations preserve instance identity and restore submerged health`() {
        val npc = Npc(KrakenRules.Pool, KrakenCove.BossSpawn)
        val uid = npc.uid
        npc.transmog(checkNotNull(ServerCacheManager.getNpc(KrakenRules.Boss.asRSCM())), Int.MAX_VALUE)
        npc.copyStats(npc.visType)
        assertEquals(uid, npc.uid)
        assertEquals(255, npc.hitpoints)
        assertEquals(KrakenRules.Pool.asRSCM(), npc.id)
        npc.hitpoints = 1
        npc.resetTransmog()
        npc.copyStats(npc.type)
        assertEquals(255, npc.hitpoints)
        assertEquals(KrakenRules.Pool.asRSCM(), npc.visType.id)
    }
}
