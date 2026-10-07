package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.Direction
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class DraynorVillageInteractionsTest {
    @Test fun `native trapdoor opens and both directions reach the requested walkable landing`() {
        val f = Fixture()
        val shut = f.native("loc.vampire_trap1")
        f.player.coords = CoordGrid(3117, 3244)
        f.op(shut)
        val opened = f.at(shut)
        assertEquals("loc.vampire_trap2".asRSCM(), opened.id)
        assertEquals(shut.angle, opened.angle)
        assertEquals(shut.shape, opened.shape)
        f.op(opened)
        assertEquals(CoordGrid(3117, 3244), f.player.coords)
        assertEquals("seq.human_reachforladder".asRSCM(), f.player.pendingSequence.id)
        f.tick()
        assertEquals(CoordGrid(3118, 9644), f.player.coords)
        f.assertWalkable()

        val ladder = f.native("loc.vampire_ladder")
        assertTrue(RouteFinding(f.collision).findRoute(
            level = 0, srcX = f.player.coords.x, srcZ = f.player.coords.z,
            destX = ladder.x, destZ = ladder.z, destWidth = ladder.width,
            destLength = ladder.length, locShape = ladder.shapeId, locAngle = ladder.angleId,
            blockAccessFlags = ladder.forceApproachFlags, moveNear = false,
        ).success)
        f.op(ladder)
        repeat(2) { f.tick() }
        assertEquals(CoordGrid(3117, 3244), f.player.coords)
        f.assertWalkable()
        assertEquals("loc.vampire_trap2".asRSCM(), f.at(shut).id)
    }

    @Test fun `interrupted climb never transfers the player to the other floor`() {
        val f = Fixture()
        val ladder = f.native("loc.vampire_ladder")
        f.player.coords = CoordGrid(3118, 9644)
        f.op(ladder)
        assertTrue(f.player.activeCoroutine?.isSuspended == true)
        f.cancel()
        repeat(2) { f.tick() }
        assertEquals(CoordGrid(3118, 9644), f.player.coords)
    }

    @Test fun `all Draynor tellers face east on startup and spawn without rotating other bankers`() {
        val f = Fixture()
        val tellers = listOf(
            Npc("npc.banker1", CoordGrid(3088, 3242)),
            Npc("npc.banker1", CoordGrid(3090, 3245)),
            Npc("npc.banker2", CoordGrid(3090, 3242)),
            Npc("npc.banker2", CoordGrid(3090, 3243)),
        )
        val elsewhere = Npc("npc.banker1", CoordGrid(3100, 3242)).apply {
            respawnDirection(Direction.South)
        }
        f.npcs.add(elsewhere, Int.MAX_VALUE)
        for (npc in tellers) {
            f.npcs.add(npc, Int.MAX_VALUE)
            assertEquals(Direction.East, npc.respawnDir)
            npc.respawnDirection(Direction.West)
        }
        f.events.publish(GameLifecycle.Startup)
        assertTrue(tellers.all { it.respawnDir == Direction.East })
        assertEquals(Direction.South, elsewhere.respawnDir)
    }

    private class Fixture {
        val events = EventBus()
        val collision = CollisionFlagMap()
        private val clock = MapClock(100)
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val npcList = NpcList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        val npcs = NpcRepository(clock, npcRegistry, npcList)
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
        )
        private val locs = LocRepository(clock, LocRegistry(storage, normal,
            LocRegistryRegion(updates, collision, storage, regions)), regions)
        private val native = mutableListOf<BoundLocInfo>()
        val player = Player().apply {
            client = SilentClient()
            currentMapClock = 100
            processedMapClock = 100
        }
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
        )
        private var coroutine = GameCoroutine()
        private var result: Result<Unit>? = null

        init {
            val builder = GameMapBuilder()
            for ((z, data) in mapData) {
                val tiles = MapTileDecoder.decode(InlineByteBuf(data.first))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(data.second))
                for (plane in 0..3) for (x in 3072..3135 step 8) for (y in z * 64 until (z + 1) * 64 step 8) {
                    collision.allocateIfAbsent(x, y, plane)
                }
                GameMapDecoder.putMaps(collision, MapSquareKey(48, z), tiles)
                GameMapDecoder.putLocs(builder, collision, MapSquareKey(48, z), tiles, spawns)
            }
            for ((packed, zone) in builder.zoneBuilders) {
                val zoneKey = ZoneKey(packed)
                val base = zoneKey.toCoords()
                for (entry in zone.build().byte2IntEntrySet()) {
                    val key = LocZoneKey(entry.byteKey)
                    val entity = LocEntity(entry.intValue)
                    storage.mapLocs[zoneKey, key] = entity
                    val loc = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                    native += BoundLocInfo(loc, checkNotNull(ServerCacheManager.getObject(entity.id)))
                }
            }
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(DraynorSewer(locs)) { scripts.startup() }
            with(DraynorBankFacing(npcs)) { scripts.startup() }
        }

        fun native(symbol: String) = native.single { it.id == symbol.asRSCM() }
        fun at(loc: BoundLocInfo): BoundLocInfo {
            val current = checkNotNull(locs.findExact(loc.coords, loc.shape))
            return BoundLocInfo(current, checkNotNull(ServerCacheManager.getObject(current.id)))
        }
        fun op(loc: BoundLocInfo) {
            coroutine = GameCoroutine()
            player.activeCoroutine = coroutine
            result = null
            val action: suspend () -> Unit = {
                val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1)
                assertTrue(events.publish(ProtectedAccess(player, coroutine, context), checkNotNull(event)))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            result?.getOrThrow()
        }
        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            coroutine.advance()
            result?.getOrThrow()
        }
        fun cancel() { coroutine.cancel(); result = null }
        fun assertWalkable() {
            val c = player.coords
            assertEquals(0, collision[c.x, c.z, c.level] and
                (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR))
        }
    }

    private class SilentClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private lateinit var mapData: Map<Int, Pair<ByteArray, ByteArray>>

        @JvmStatic @BeforeAll fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                mapData = listOf(50, 150).associateWith { z ->
                    checkNotNull(cache.data(MAPS, (48 shl 8) or z, 0)) to
                        checkNotNull(cache.data(MAPS, (48 shl 8) or z, 1))
                }
            } finally { cache.close() }
        }
    }
}
