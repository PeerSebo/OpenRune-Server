package org.rsmod.content.bosses.skotizo

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
class SkotizoMapTest {
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

    @BeforeAll fun nativeMap() = load(listOf(SkotizoRules.Entry, SkotizoRules.Exit))

    @Test fun `native altar is reachable from the grave and return landing`() {
        route(SkotizoRules.Exit, nativeLocs.single { it.id == "loc.cata_altar".asRSCM() })
    }

    @Test fun `all four copied portals remain reachable from the arena entrance`() {
        val portals = nativeLocs.filter { it.id == "loc.cata_boss_exit_portal".asRSCM() }
        assertEquals(4, portals.size)
        for (portal in portals) route(SkotizoRules.Entry, portal)
    }

    @Test fun `boss helpers and every altar stand on reachable native floor`() {
        open(SkotizoRules.Entry)
        for (site in SkotizoRules.Altars + SkotizoRules.Demons + CoordGrid(2276, 5659)) {
            open(site)
            val path = RouteFinding(collision).findRoute(level = 0, srcX = SkotizoRules.Entry.x, srcZ = SkotizoRules.Entry.z,
                destX = site.x, destZ = site.z, destWidth = 1, destLength = 1, locShape = -2, moveNear = false)
            assertTrue(path.success, "Cannot reach summoned NPC at $site")
        }
        for (x in 0..4) for (z in 0..4) open(SkotizoRules.BossSpawn.translate(x, z))
    }

    @Test fun `directional altars transform without changing their identity and can close again`() {
        for (index in 0..3) {
            val npc = Npc(SkotizoRules.Closed[index], SkotizoRules.Altars[index])
            assertEquals(1, npc.hitpoints)
            assertFalse(npc.visType.hasOp(2))
            val uid = npc.uid
            npc.transmog(checkNotNull(ServerCacheManager.getNpc(SkotizoRules.Open[index].asRSCM())), Int.MAX_VALUE)
            npc.copyStats(npc.visType)
            assertEquals(100, npc.hitpoints)
            assertEquals(uid, npc.uid)
            npc.resetTransmog(); npc.copyStats(npc.type)
            assertEquals(1, npc.hitpoints)
        }
    }
}
