package org.rsmod.content.bosses.thermy

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThermyTest {
    private val collision = CollisionFlagMap()
    private val landings = listOf(SmokeDevilCave.Inside, SmokeDevilCave.Outside,
        SmokeDevilCave.BossInside, SmokeDevilCave.BossOutside)

    @BeforeAll fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            for ((mx, mz) in (landings + CoordGrid(2360, 9452)).map { it.x / 64 to it.z / 64 }.toSet()) {
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

    @Test fun `requires unboosted 93 with exactly one off-task diary kill`() {
        assertFalse(ThermyRequirements.canFight(92, true, 0))
        assertTrue(ThermyRequirements.canFight(93, false, 0))
        assertFalse(ThermyRequirements.canFight(93, false, 1))
        assertTrue(ThermyRequirements.canFight(93, true, 500))
    }

    @Test fun `packed boss has native stats ranged reach and can pass through entities`() {
        val npc = Npc("npc.smoke_devil_boss", CoordGrid(2360, 9452))
        assertEquals(240, npc.hitpoints)
        assertEquals(310, npc.visType.ranged)
        assertEquals(1, npc.visType.magic)
        assertEquals(8, npc.attackRange)
        assertEquals(MoveRestrict.PassThru, npc.moveRestrict)
        assertEquals(15, npc.visType.respawnRate)
    }

    @Test fun `all four native entrances register`() {
        val bus = EventBus()
        val ctx = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        with(SmokeDevilCave()) { ctx.startup() }
        for (symbol in listOf("loc.smokedevil_cave_entrance", "loc.slayer_cave_passage_smokedevil_exit",
            "loc.slayer_cave_smokedevil_boss_entrance", "loc.slayer_cave_smokedevil_boss_exit"))
            assertTrue(bus.contains(LocEvents.Op1::class.java, symbol.asRSCM()), symbol)
    }

    @Test fun `all arrivals use walkable native floor`() {
        val mask = CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.LOC
        for (tile in landings) assertEquals(0, collision[tile.x, tile.z, tile.level] and mask,
            "Blocked arrival $tile")
        val start = SmokeDevilCave.BossInside
        val route = RouteFinding(collision).findRoute(level = 0, srcX = start.x, srcZ = start.z,
            destX = 2360, destZ = 9452, destWidth = 4, destLength = 4, locShape = -2, moveNear = false)
        assertTrue(route.success, "Cannot reach boss from passage")
    }
}
