package org.rsmod.content.generic.locs.ladders

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
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FaladorMineStairsTest {
    private val collision = CollisionFlagMap()
    private val locs = mutableListOf<BoundLocInfo>()

    @BeforeAll
    fun nativeMap() {
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            for ((mx, mz) in listOf(47 to 52, 47 to 152, 48 to 54, 48 to 154)) {
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
        } finally {
            cache.close()
        }
    }

    @Test
    fun `both directions land on native open floor without a climb sequence`() {
        val flights = listOf(
            Triple("loc.stairs_cellar", CoordGrid(3058, 3376), CoordGrid(3058, 9776)),
            Triple("loc.stairs_falador", CoordGrid(3059, 9776), CoordGrid(3061, 3376)),
        )
        for ((symbol, tile, landing) in flights) for (justArrived in listOf(false, true)) {
            val stairs = locs.single { it.coords == tile && it.id == symbol.asRSCM() }
            val events = EventBus()
            val player = Player().apply {
                client = SilentClient()
                coords = tile.translate(if (symbol == "loc.stairs_cellar") 3 else -1, 0)
                currentMapClock = 100
                processedMapClock = 100
                pendingSequence = EntitySeq.NULL
                if (justArrived) lastMovement = 99
            }
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events },
                getCollision = { collision },
                getAreaChecker = { unused<AreaChecker>() },
                getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            )
            with(DungeonEntranceScript(unused<LocRepository>())) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                val type = checkNotNull(ServerCacheManager.getObject(stairs.id))
                assertTrue(events.publish(this, LocEvents.Op1(stairs, stairs, type)))
            })
            assertEquals(EntitySeq.NULL, player.pendingSequence)
            if (justArrived) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                player.activeCoroutine?.advance()
            }
            assertEquals(landing, player.coords)
            assertEquals(EntitySeq.NULL, player.pendingSequence)
            assertFalse(player.isAccessProtected)
            assertEquals(0, collision[landing.x, landing.z, landing.level] and
                (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR))

            val returnSymbol = if (landing.z < 6400) "loc.stairs_cellar" else "loc.stairs_falador"
            val returnTile = if (landing.z < 6400) CoordGrid(3058, 3376) else CoordGrid(3059, 9776)
            val returnStairs = locs.single { it.id == returnSymbol.asRSCM() && it.coords == returnTile }
            val route = RouteFinding(collision).findRoute(
                level = landing.level, srcX = landing.x, srcZ = landing.z,
                destX = returnStairs.x, destZ = returnStairs.z,
                destWidth = returnStairs.width, destLength = returnStairs.length,
                locShape = returnStairs.shapeId, locAngle = returnStairs.angleId,
                blockAccessFlags = returnStairs.forceApproachFlags, moveNear = false,
            )
            assertTrue(route.success, "The player must be able to click the return staircase")
        }
    }

    @Test
    fun `Edgeville dungeon ladder lands west of the trapdoor from either approach`() {
        val ladder = locs.single {
            it.coords == CoordGrid(3097, 9867) && it.id == "loc.ladder_from_cellar".asRSCM()
        }
        for (approach in listOf(CoordGrid(3097, 9868), CoordGrid(3096, 9867))) {
            val events = EventBus()
            val player = Player().apply {
                client = SilentClient()
                coords = approach
                currentMapClock = 100
                processedMapClock = 100
            }
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getCollision = { collision },
                getAreaChecker = { unused<AreaChecker>() },
                getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            )
            with(DungeonLadderScript()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                assertTrue(events.publish(this, LocContentEvents.Op1(
                    ladder, ladder, checkNotNull(ServerCacheManager.getObject(ladder.id)),
                    "content.dungeonladder_up".asRSCM(),
                )))
            })
            assertEquals(approach, player.coords)
            assertNotEquals(EntitySeq.NULL, player.pendingSequence)
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.activeCoroutine?.advance()
            val landing = CoordGrid(3096, 3468)
            assertEquals(landing, player.coords)
            assertEquals(0, collision[landing.x, landing.z, landing.level] and CollisionFlag.LOC)
        }
    }

    @Test
    fun `old surface landing is inside the blocked staircase footprint`() {
        assertNotEquals(0, collision[3059, 3376, 0] and CollisionFlag.LOC)
    }

    private class SilentClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    private inline fun <reified T> unused(): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
    }
}
