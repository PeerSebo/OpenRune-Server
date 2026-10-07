package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DraynorManorSceneryTest {
    @Test fun `native bookcase lever stays down until both shelves close even after cancelled movement`() {
        val f = Fixture()
        val lever = f.native("loc.hauntedleverup")
        val left = f.native("loc.hauntedbookcasel")
        val right = f.native("loc.hauntedbookcaser")
        f.op(lever, InteractionOp.Op1)
        assertEquals(lever.id, f.at(lever)!!.id)
        f.tick()
        assertEquals("loc.hauntedleverdown".asRSCM(), f.at(lever)!!.id)
        assertNull(f.at(left))
        assertNull(f.at(right))
        f.cancel()
        var restored = false
        repeat(12) {
            f.tick()
            if (f.at(left) != null) restored = true
            assertEquals(restored, f.at(right) != null)
            assertEquals(if (restored) lever.id else "loc.hauntedleverdown".asRSCM(), f.at(lever)!!.id)
        }
        assertTrue(restored)
    }

    @Test fun `native wardrobes support open search close and the skeleton variant`() {
        for (skeleton in listOf(false, true)) {
            val f = Fixture(skeleton)
            val mapped = f.native("loc.draynor_wardrobe", "loc.draynor_wardrobe_open", "loc.draynor_wardrobe_open_skeleton",
                "loc.spookywardrobe", "loc.spookywardrobe_open", "loc.spookywardrobe_open_skeleton")
            val base = if (mapped.id in 388..390) "loc.spookywardrobe" else "loc.draynor_wardrobe"
            if (mapped.id != base.asRSCM()) f.op(mapped, InteractionOp.Op3)
            val closedLoc = f.at(mapped)!!
            val closed = BoundLocInfo(closedLoc, checkNotNull(ServerCacheManager.getObject(closedLoc.id)))
            f.player.coords = closed.coords.translate(1, 0)
            f.op(closed, InteractionOp.Op1)
            val openLoc = f.at(closed)!!
            val opened = BoundLocInfo(openLoc, checkNotNull(ServerCacheManager.getObject(openLoc.id)))
            val symbol = if (skeleton) "${base}_open_skeleton" else "${base}_open"
            assertEquals(symbol.asRSCM(), opened.id)
            assertEquals(closed.angle, opened.angle)
            f.op(opened, InteractionOp.Op2)
            assertTrue(f.output().contains(if (skeleton) "Eek! There's a skeleton in there!" else "You search the wardrobe but find nothing."))
            assertFalse(f.output().contains("Nothing interesting happens."))
            f.op(opened, InteractionOp.Op3)
            assertEquals(closed.id, f.at(closed)!!.id)
            repeat(101) { f.tick() }
            assertEquals(closed.id, f.at(closed)!!.id)
        }
    }

    private class Fixture(skeleton: Boolean = false) {
        private val clock = MapClock(100)
        private val events = EventBus()
        private val collision = CollisionFlagMap()
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3095, 3360)
            currentMapClock = 100
            processedMapClock = 100
        }
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision },
        )
        private val repo: LocRepository
        private var coroutine = GameCoroutine()
        private var result: Result<Unit>? = null
        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            for (loc in nativeLocs) {
                storage.mapLocs[ZoneKey.from(loc.coords), LocZoneKey(loc.coords.x and 7, loc.coords.z and 7, loc.layer)] = loc.entity
            }
            for (level in 0..2) for (x in 3072..3135 step 8) for (z in 3328..3391 step 8) {
                collision.allocateIfAbsent(x, z, level)
            }
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcs = NpcList()
            val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
                NpcRegistry(npcs, collision, events), ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            repo = LocRepository(clock, LocRegistry(storage, normal,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(DraynorManorSearch(repo)) { scripts.startup() }
            val random = object : GameRandom by DefaultGameRandom(1) {
                override fun randomBoolean(maxExclusive: Int): Boolean {
                    assertEquals(21, maxExclusive)
                    return skeleton
                }
            }
            with(DraynorWardrobes(repo, random)) { scripts.startup() }
        }
        fun native(vararg symbols: String): BoundLocInfo {
            val ids = symbols.map { it.asRSCM() }.toSet()
            val loc = nativeLocs.first { it.id in ids && it.coords.level == 0 }
            return BoundLocInfo(loc, checkNotNull(ServerCacheManager.getObject(loc.id)))
        }
        fun at(loc: BoundLocInfo) = repo.findExact(loc.coords, loc.shape)
        fun op(loc: BoundLocInfo, op: InteractionOp) {
            coroutine = GameCoroutine()
            player.activeCoroutine = coroutine
            result = null
            val action: suspend () -> Unit = {
                val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
                assertNotNull(event)
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
            val method = repo.javaClass.declaredMethods.single { it.name.startsWith("processDurations") }
            method.invoke(repo)
        }
        fun cancel() {
            coroutine.cancel()
            assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
            result = null
        }
        fun output() = client.messages.joinToString("\n")
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private lateinit var nativeLocs: List<LocInfo>

        @JvmStatic @BeforeAll fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                val scenery = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, (48 shl 8) or 52, 1))))
                nativeLocs = scenery.spawns.map(::MapLocDefinition).map {
                    LocInfo(LocLayerConstants.of(it.shape), CoordGrid(3072 + it.localX, 3328 + it.localZ, it.level),
                        LocEntity(it.id, it.shape, it.angle))
                }
            } finally { cache.close() }
        }
    }
}
