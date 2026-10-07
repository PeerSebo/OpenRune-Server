package org.rsmod.content.bosses.sarachnis

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SarachnisTest {
    private val collision = CollisionFlagMap()

    @BeforeAll fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            for ((mx, mz) in listOf(28 to 154, 28 to 155, 26 to 55)) {
                val id = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, id, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, id, 1))))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8)
                    for (z in mz * 64 until (mz + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(collision, MapSquareKey(mx, mz), tiles)
                GameMapDecoder.putLocs(GameMapBuilder(), collision, MapSquareKey(mx, mz), tiles, locs)
            }
        } finally { cache.close() }
    }

    @Test fun `four ordinary attacks precede each web`() {
        val cycle = SarachnisCycle()
        repeat(3) {
            repeat(4) { assertFalse(cycle.nextIsWeb()) }
            assertTrue(cycle.nextIsWeb())
        }
    }

    @Test fun `waves occur once at both thresholds despite healing or skipping a threshold`() {
        val cycle = SarachnisCycle()
        assertEquals(0, cycle.claimWaves(400))
        assertEquals(1, cycle.claimWaves(264))
        assertEquals(0, cycle.claimWaves(300))
        assertEquals(0, cycle.claimWaves(250))
        assertEquals(1, cycle.claimWaves(132))
        assertEquals(0, cycle.claimWaves(20))
        assertEquals(2, SarachnisCycle().claimWaves(100))
        assertEquals(0, SarachnisCycle().claimWaves(0))
    }

    @Test fun `private room coordinates remain isolated from the public crypt and other planes`() {
        val source = Sarachnis.Spawn
        val private = source.translate(5000, 5000)
        assertTrue(Sarachnis.inRoom(source, SarachnisAccess.Inside))
        assertFalse(Sarachnis.inRoom(source, SarachnisAccess.Outside))
        assertFalse(Sarachnis.inRoom(private, source))
        assertFalse(Sarachnis.inRoom(source, CoordGrid(source.x, source.z, 1)))
        assertTrue(Sarachnis.inRoom(private, private.translate(1, 1)))
    }

    @Test fun `boss and both young have packed native health and correct distinct reaches`() {
        val boss = Npc("npc.sarachnis", Sarachnis.Spawn)
        val melee = Npc("npc.sarachnis_melee_spawn", Sarachnis.Spawn)
        val magic = Npc("npc.sarachnis_mage_spawn", Sarachnis.Spawn)
        assertEquals(400, boss.hitpoints)
        assertEquals(5, boss.size)
        assertEquals(30, melee.hitpoints)
        assertEquals(30, magic.hitpoints)
        assertEquals(1, melee.attackRange)
        assertEquals(7, magic.attackRange)
    }

    @Test fun `native crypt entrance and complete boss footprint are open and connected`() {
        val tiles = mutableListOf(SarachnisAccess.Inside, SarachnisAccess.Outside,
            SarachnisAccess.LadderInside, SarachnisAccess.LadderOutside,
            SarachnisAccess.SpiralInside, SarachnisAccess.SpiralOutside)
        for (dx in 0..4) for (dz in 0..4) tiles += Sarachnis.Spawn.translate(dx, dz)
        for (tile in tiles) assertEquals(0, collision[tile.x, tile.z, tile.level] and Sarachnis.FLOOR_BLOCK,
            "Blocked native Sarachnis tile $tile")
        val start = SarachnisAccess.Inside
        assertTrue(RouteFinding(collision).findRoute(level = 0, srcX = start.x, srcZ = start.z,
            destX = Sarachnis.Spawn.x, destZ = Sarachnis.Spawn.z, destWidth = 5, destLength = 5,
            locShape = -2, moveNear = false).success)
        for ((arrival, stair) in listOf(SarachnisAccess.SpiralOutside to CoordGrid(1669, 3567),
            SarachnisAccess.SpiralInside to CoordGrid(1798, 9967))) {
            assertTrue(RouteFinding(collision).findRoute(level = 0, srcX = arrival.x, srcZ = arrival.z,
                destX = stair.x, destZ = stair.z, destWidth = 2, destLength = 2,
                locShape = -2, moveNear = false).success, "Cannot approach spiral stairs from $arrival")
        }
    }
}
