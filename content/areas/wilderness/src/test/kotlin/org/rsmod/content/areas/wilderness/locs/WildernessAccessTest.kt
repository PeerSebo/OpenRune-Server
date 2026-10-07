package org.rsmod.content.areas.wilderness.locs

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
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WildernessAccessTest {
    private lateinit var collision: CollisionFlagMap
    private val nativeLocs = mutableListOf<BoundLocInfo>()
    private val events = EventBus()

    @BeforeAll fun loadNativeMaps() {
        collision = CollisionFlagMap()
        val cache = ServerCacheManager.init(241)
        try {
            val coords = WildernessBossPassages.Passages.flatMap { listOf(it.source, it.destination) } +
                FeroxBarriers.Gates.flatMap { it.leaves }
            val squares = coords.map { it.x / 64 to it.z / 64 }.toSet()
            val builder = GameMapBuilder()
            for ((mx, mz) in squares) {
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8) for (z in mz * 64 until (mz + 1) * 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
                GameMapDecoder.putMaps(collision, MapSquareKey(mx, mz), tiles)
                GameMapDecoder.putLocs(builder, collision, MapSquareKey(mx, mz), tiles, locs)
            }
            for ((packedZone, zoneBuilder) in builder.zoneBuilders) {
                val base = ZoneKey(packedZone).toCoords()
                for (entry in zoneBuilder.build().byte2IntEntrySet()) {
                    val key = LocZoneKey(entry.byteKey)
                    val entity = LocEntity(entry.intValue)
                    val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                    nativeLocs += BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id)))
                }
            }
        } finally { cache.close() }
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(WildernessBossPassages()) { context.startup() }
        with(FeroxBarriers()) { context.startup() }
    }

    @Test fun `native click options resolve registered passage and barrier handlers`() {
        val symbols = WildernessBossPassages.Passages.map { it.symbol } + FeroxBarriers.BarrierSymbols
        for (symbol in symbols.distinct()) {
            assertTrue(events.contains(LocEvents.Op1::class.java, symbol.asRSCM()), "No op1 registered for $symbol")
        }
        for (passage in WildernessBossPassages.Passages) {
            assertNotNull(nativeLocs.firstOrNull { it.id == passage.symbol.asRSCM() && it.coords == passage.source },
                "Passage does not exist on the player's native plane: $passage")
        }
    }

    @Test fun `every arrival is open native floor with space for ordinary walking`() {
        for (passage in WildernessBossPassages.Passages) {
            val tile = passage.destination
            assertFalse(blocked(tile), "Blocked arrival for $passage; nearby=${nearbyOpen(tile)}")
            val finder = RouteFinding(collision)
            assertTrue(nearbyOpen(tile).any { destination ->
                destination != tile && finder.findRoute(level = tile.level, srcX = tile.x, srcZ = tile.z,
                    destX = destination.x, destZ = destination.z, moveNear = false).success
            }, "Cannot walk after arrival: $passage")
        }
    }

    @Test fun `ordinary routing reaches crevice memorial both KBD ladders and tomb exits`() {
        val starts = listOf(CoordGrid(3070, 3857), CoordGrid(3067, 10254), CoordGrid(3224, 3788),
            CoordGrid(3182, 3683), CoordGrid(3295, 10197, 1), CoordGrid(1887, 11541, 1))
        for ((index, passage) in WildernessBossPassages.Passages.withIndex()) {
            val loc = nativeLocs.first { it.id == passage.symbol.asRSCM() && it.coords == passage.source }
            assertRoutesTo(starts[index], loc)
        }
    }

    @Test fun `KBD chamber arrival can reach its existing lair lever and return ladder`() {
        val from = WildernessBossPassages.Passages.first().destination
        for (symbol in listOf("loc.dragonkinginlever", "loc.wildymirrorladder1")) {
            val loc = nativeLocs.first { it.id == symbol.asRSCM() && it.level == from.level }
            assertRoutesTo(from, loc)
        }
    }

    @Test fun `all eight barrier leaves are reachable and crossable in both directions`() {
        for (gate in FeroxBarriers.Gates) for (inner in gate.leaves) {
            val loc = nativeLocs.first { it.coords == inner && it.id in FeroxBarriers.BarrierSymbols.map { symbol -> symbol.asRSCM() } }
            val outer = inner.translate(gate.dx, gate.dz)
            for (start in listOf(inner, outer)) {
                assertRoutesTo(start, loc)
                val player = Player().apply { coords = start; currentMapClock = 100; processedMapClock = 100 }
                player.forcedRoute = true
                val route = gate.route(start)
                player.routeDestination.addAll(route)
                val movement = PlayerMovementProcessor(collision, RouteFactory(collision), StepFactory(collision), events)
                repeat(4) { tick ->
                    player.currentMapClock = 100 + tick
                    player.processedMapClock = 100 + tick
                    player.previousCoords = player.coords
                    player.moveSpeed = MoveSpeed.Walk
                    movement.process(player)
                }
                assertEquals(if (start == inner) outer else inner, player.coords, "Barrier did not cross from $start")
                assertFalse(blocked(player.coords), "Barrier leaves player in solid scenery")
            }
        }
    }

    @Test fun `teleblock prevents entering but does not trap a player inside`() {
        assertTrue(FeroxBarriers.canCross(false, 0))
        assertFalse(FeroxBarriers.canCross(false, 1))
        assertTrue(FeroxBarriers.canCross(true, 100))
    }

    private fun assertRoutesTo(start: CoordGrid, loc: BoundLocInfo) {
        assertFalse(blocked(start), "Test starts in solid scenery at $start; nearby=${nearbyOpen(start)}")
        val route = RouteFinding(collision).findRoute(level = start.level, srcX = start.x, srcZ = start.z,
            destX = loc.x, destZ = loc.z, destWidth = loc.width, destLength = loc.length,
            locShape = loc.shapeId, locAngle = loc.angleId, blockAccessFlags = loc.forceApproachFlags,
            moveNear = false)
        assertTrue(route.success, "Normal click cannot route from $start to $loc")
        val end = route.lastOrNull()?.let { CoordGrid(it.x, it.z, start.level) } ?: start
        val player = Player().apply { coords = end }
        assertTrue(BoundValidator(collision).touches(player.avatar, loc), "Route ends outside actual op reach: $end to $loc")
    }

    private fun blocked(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR) != 0

    private fun nearbyOpen(tile: CoordGrid): List<CoordGrid> = buildList {
        for (dx in -2..2) for (dz in -2..2) {
            val point = tile.translate(dx, dz)
            if (!blocked(point)) add(point)
        }
    }
}
