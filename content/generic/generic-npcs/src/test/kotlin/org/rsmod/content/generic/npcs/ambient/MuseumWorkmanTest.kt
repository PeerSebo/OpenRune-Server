package org.rsmod.content.generic.npcs.ambient

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.util.BlockWalk
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocDefaultEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.OpDefaultEvent
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MuseumWorkmanTest {
    @Test fun `mapped workman completes two deliveries through native scenery and guard gates`() {
        val f = AmbientNpcTest.Fixture()
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            for (mx in 50..52) for (mz in 53..54) {
                val key = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val scenery = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8)
                    for (z in mz * 64 until (mz + 1) * 64 step 8) f.collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(f.collision, key, tiles)
                GameMapDecoder.putLocs(builder, f.collision, key, tiles, scenery)
            }
            for ((zone, data) in builder.zoneBuilders) f.storage.mapLocs[zone] = data.build()
        } finally { cache.close() }
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val workman = f.npc(MuseumWorkman.Empty, mappedSpawn(MuseumWorkman.Empty))
        val loader = f.npc("npc.vm_digworkman_loader", mappedSpawn("npc.vm_digworkman_loader"))
        val guards = MuseumWorkman.Gates.map { f.npc(it.guard, mappedSpawn(it.guard)) }
        f.npc("npc.fai_varrock_guard02", CoordGrid(3271, 3428))
        f.npc("npc.fai_varrock_guard02_f_variant02", CoordGrid(3275, 3428))
        f.npc("npc.fai_varrock_guard_captain02", CoordGrid(3273, 3430))
        val player = Player().apply {
            coords = CoordGrid(3277, 3428)
            addBlockWalkCollision(f.collision, coords)
        }
        assertTrue(workman.routeDestination.isNotEmpty())
        val tick = WorkmanTick(f)
        var loads = 0
        var deliveries = 0
        repeat(800) {
            val previousType = workman.visType.id
            tick.process()
            if (previousType != workman.visType.id) {
                if (workman.isVisType(MuseumWorkman.Loaded)) {
                    loads++
                    assertEquals(CoordGrid(3358, 3414), workman.coords)
                } else {
                    deliveries++
                    assertEquals(CoordGrid(3264, 3444), workman.coords)
                    assertEquals(-1, f.info(workman).sequences.last(), "Held dumping pose must clear before return")
                }
            }
        }
        assertTrue(loads >= 2, "Workman stalled at ${workman.coords}: ${workman.routeDestination}")
        assertTrue(deliveries >= 2, "Workman stalled at ${workman.coords}: ${workman.routeDestination}")
        assertTrue("seq.vm_wheelbarrow_loader".asRSCM() in f.info(loader).sequences)
        assertTrue(guards.any { f.info(it).messages.isNotEmpty() })
        assertNotEquals(0, f.collision[player.x, player.z, 0] and CollisionFlag.BLOCK_NPCS)
    }

    @Test fun `workman crosses NPC and player blockers without removing their collision`() {
        val f = AmbientNpcTest.Fixture()
        for (x in 3256..3370) for (z in 3400..3456) f.collision.allocateIfAbsent(x, z, 0)
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val workman = f.npc(MuseumWorkman.Empty, MuseumWorkman.Spawn)
        val tick = WorkmanTick(f)
        tick.process()
        assertEquals(MuseumWorkman.Spawn.translate(1, 0), workman.coords)
        val guard = f.npc("npc.fai_varrock_guard02", workman.coords.translate(2, 0))
        val player = Player().apply {
            coords = workman.coords.translate(4, 0)
            addBlockWalkCollision(f.collision, coords)
        }
        assertEquals(MoveRestrict.PassThru, workman.moveRestrict)
        assertEquals(BlockWalk.None, workman.blockWalk)
        var crossedGuard = false
        var crossedPlayer = false
        repeat(8) {
            val before = workman.coords
            tick.process()
            assertEquals(before.translate(1, 0), workman.coords)
            crossedGuard = crossedGuard || workman.coords == guard.coords
            crossedPlayer = crossedPlayer || workman.coords == player.coords
            assertNotEquals(0, f.collision[guard.x, guard.z, 0] and CollisionFlag.BLOCK_NPCS)
            assertNotEquals(0, f.collision[player.x, player.z, 0] and CollisionFlag.BLOCK_NPCS)
        }
        assertTrue(crossedGuard)
        assertTrue(crossedPlayer)
        repeat(100) {
            val before = workman.coords
            tick.process()
            assertTrue(before.chebyshevDistance(workman.coords) <= 1)
        }
        assertTrue(workman.isVisType(MuseumWorkman.Loaded))
        assertTrue(f.info(workman).messages.contains("Great, just what my arms need!"))
    }

    @Test fun `pass through workman still stops for scenery and resumes when the way opens`() {
        val f = AmbientNpcTest.Fixture()
        for (x in 3296..3343) for (z in 3416..3439) {
            f.collision[x, z, 0] = if (z in 3425..3426) 0 else CollisionFlag.LOC
        }
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val workman = f.npc(MuseumWorkman.Empty, MuseumWorkman.Spawn)
        val tick = WorkmanTick(f)
        tick.process()
        val obstruction = workman.coords.translate(2, 0)
        for (z in 3425..3426) f.collision.add(obstruction.x, z, 0, CollisionFlag.LOC)
        val stalledAt = workman.coords
        repeat(12) { tick.process() }
        assertEquals(stalledAt, workman.coords)
        assertTrue(workman.isSlotAssigned)
        for (z in 3425..3426) f.collision.remove(obstruction.x, z, 0, CollisionFlag.LOC)
        tick.process()
        assertEquals(stalledAt.translate(1, 0), workman.coords)
    }

    private class WorkmanTick(private val fixture: AmbientNpcTest.Fixture) {
        private val postTick = NpcPostTickProcess(fixture.npcList, fixture.npcRegistry,
            NpcFaceSquareProcessor(), GameExceptionHandler { failure, message ->
                throw AssertionError(message().toString(), failure)
            })

        fun process() {
            fixture.clock.tick()
            for (npc in fixture.npcList) {
                npc.currentMapClock = fixture.clock.cycle
                npc.processedMapClock = fixture.clock.cycle
                npc.previousCoords = npc.coords
                if (npc.canProcess) {
                    fixture.timers.process(npc)
                    fixture.movement.process(npc)
                }
            }
            postTick.process()
        }
    }

    @Test fun `only the assigned workman crosses an intact open gate while player collision stays blocked`() {
        val f = AmbientNpcTest.Fixture()
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        val gate = MuseumWorkman.DigsiteGate
        val guard = f.npc(gate.guard, gate.guardPosition)
        val workman = f.npc(MuseumWorkman.Loaded, CoordGrid(3297, 3428)).apply { prepareJourney(CoordGrid(3293, 3428)) }
        val destination = CoordGrid(3293, 3428)
        listOf(wall(24561, 3296, 3429, 0), wall(24560, 3296, 3428, 0)).forEach { f.putMapLoc(it) }
        assertFalse(script.crossGate(workman, destination))
        script.operateGate(guard, gate)
        for (loc in gate.changes.values.filterNotNull()) {
            val type = checkNotNull(ServerCacheManager.getObject(loc.id))
            assertNotEquals("Open", type.actions.getOpOrNull(0))
        }
        val steps = StepFactory(f.collision)
        val from = CoordGrid(3296, 3428)
        val into = CoordGrid(3295, 3428)
        assertEquals(CoordGrid.NULL, steps.validated(from, into, size = 1))
        val unrelated = f.npc(MuseumWorkman.Loaded, CoordGrid(3297, 3428))
        assertFalse(script.crossGate(unrelated, destination))
        assertFalse(script.crossGate(workman, CoordGrid(3298, 3428)))
        assertTrue(script.crossGate(workman, destination))
        assertEquals(CoordGrid(3295, 3428), workman.coords)
        assertEquals(CoordGrid.NULL, steps.validated(from, into, size = 1))
        workman.coords = CoordGrid(3297, 3428)
        f.locs.add(wall(24561, 3296, 3429, 0), Int.MAX_VALUE)
        assertFalse(script.crossGate(workman, destination))
    }

    @Test fun `museum guard opens both leaves and restores the exact closed map after passage`() {
        val f = AmbientNpcTest.Fixture()
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val gate = MuseumWorkman.MuseumGate
        val closed = listOf(wall(24567, 3264, 3441, 1), wall(24565, 3265, 3441, 1))
        for (loc in closed) f.putMapLoc(loc)
        val guard = f.npc(gate.guard, gate.guardPosition)
        val workman = f.npc(MuseumWorkman.Loaded, CoordGrid(3264, 3440)).apply { prepareJourney(MuseumWorkman.UnloadPosition) }

        f.timers.process(guard)
        for ((coords, expected) in gate.changes) assertEquals(expected, f.locs.findExact(coords, LocShape.WallStraight))
        for (loc in gate.changes.values.filterNotNull()) {
            assertNotEquals("Open", checkNotNull(ServerCacheManager.getObject(loc.id)).actions.getOpOrNull(0))
        }
        assertEquals(gate.guardPosition, guard.coords)
        workman.activeCoroutine = GameCoroutine("interrupted-workman")
        f.timers.process(guard)
        assertEquals(gate.changes[CoordGrid(3264, 3442)], f.locs.findExact(CoordGrid(3264, 3442), LocShape.WallStraight))

        workman.coords = CoordGrid(3264, 3435)
        repeat(3) { f.timers.process(guard) }
        for (loc in closed) assertEquals(loc, f.locs.findExact(loc.coords, LocShape.WallStraight))
        assertNull(f.locs.findExact(CoordGrid(3264, 3442), LocShape.WallStraight))
        assertNull(f.locs.findExact(CoordGrid(3265, 3442), LocShape.WallStraight))
    }

    @Test fun `fence guard greets loaded workman and does not overwrite subsequent scenery changes`() {
        val f = AmbientNpcTest.Fixture()
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val gate = MuseumWorkman.DigsiteGate
        listOf(wall(24561, 3296, 3429, 0), wall(24560, 3296, 3428, 0)).forEach { f.putMapLoc(it) }
        val guard = f.npc(gate.guard, gate.guardPosition)
        assertEquals(Direction.North, guard.respawnDir)
        assertEquals(guard.coords.translate(0, 1), guard.pendingFaceSquare)
        val workman = f.npc(MuseumWorkman.Loaded, CoordGrid(3297, 3428)).apply { prepareJourney(CoordGrid(3293, 3428)) }
        f.timers.process(guard)
        assertEquals(listOf("Keep 'em coming!"), f.info(guard).messages)
        assertEquals(listOf("Bleah"), f.info(workman).messages)
        val replacement = wall(24565, 3294, 3429, 1)
        f.locs.add(replacement, Int.MAX_VALUE)
        workman.coords = CoordGrid(3289, 3428)
        repeat(3) { f.timers.process(guard) }
        assertEquals(replacement, f.locs.findExact(replacement.coords, LocShape.WallStraight))
        assertEquals(wall(24560, 3296, 3428, 0), f.locs.findExact(CoordGrid(3296, 3428), LocShape.WallStraight))
        assertEquals(guard.coords.translate(0, 1), guard.pendingFaceSquare)
    }

    @Test fun `guards allow two extra ticks after clearing each passage before closing`() {
        for (leg in listOf(
            GateLeg(MuseumWorkman.MuseumGate, MuseumWorkman.Loaded, CoordGrid(3264, 3440),
                MuseumWorkman.UnloadPosition, CoordGrid(3264, 3442)),
            GateLeg(MuseumWorkman.MuseumGate, MuseumWorkman.Empty, CoordGrid(3264, 3442),
                CoordGrid(3264, 3438), CoordGrid(3264, 3439)),
            GateLeg(MuseumWorkman.DigsiteGate, MuseumWorkman.Loaded, CoordGrid(3297, 3428),
                CoordGrid(3293, 3428), CoordGrid(3294, 3428)),
            GateLeg(MuseumWorkman.DigsiteGate, MuseumWorkman.Empty, CoordGrid(3294, 3428),
                CoordGrid(3298, 3428), CoordGrid(3296, 3428)),
        )) {
            val f = AmbientNpcTest.Fixture()
            val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
            val originals = closedLeaves(leg.gate)
            originals.forEach { f.putMapLoc(it) }
            val guard = f.npc(leg.gate.guard, leg.gate.guardPosition)
            val workman = f.npc(leg.type, leg.from).apply { prepareJourney(leg.destination) }
            script.operateGate(guard, leg.gate)
            assertTrue(script.crossGate(workman, leg.destination))
            workman.coords = leg.clear
            repeat(2) {
                script.operateGate(guard, leg.gate)
                for ((coords, loc) in leg.gate.changes) assertEquals(loc, f.locs.findExact(coords, LocShape.WallStraight))
            }
            script.operateGate(guard, leg.gate)
            originals.forEach { assertEquals(it, f.locs.findExact(it.coords, LocShape.WallStraight)) }
            if (leg.type == MuseumWorkman.Loaded && leg.gate == MuseumWorkman.MuseumGate) {
                workman.coords = MuseumWorkman.UnloadPosition
                workman.vars["varn.museum_workman_pause"] = 5
                repeat(10) { script.operateGate(guard, leg.gate) }
                originals.forEach { assertEquals(it, f.locs.findExact(it.coords, LocShape.WallStraight)) }
            }
        }
    }

    @Test fun `guard dialogue owns service door and fence clicks without holding up the workman`() {
        for (gate in MuseumWorkman.Gates) {
            val f = AmbientNpcTest.Fixture()
            var genericOpens = 0
            f.context.onProtectedEvent<LocDefaultEvents.Op1>(OpDefaultEvent.ID) { genericOpens++ }
            val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
            with(script) { f.context.startup() }
            val originals = closedLeaves(gate)
            originals.forEach { f.putMapLoc(it) }
            val guard = f.npc(gate.guard, gate.guardPosition)
            val messages = mutableListOf<Any>()
            val player = Player().apply {
                coords = gate.center.translate(0, -1)
                currentMapClock = 100
                processedMapClock = 100
                client = RecordingClient(messages)
            }
            val interactions = LocInteractions(BoundValidator(f.collision), f.events)
            val coroutine = GameCoroutine("museum-entrance-refusal")
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { f.events }, getAlignment = { TextAlignment() },
            )
            val clicked = BoundLocInfo(originals.first(), checkNotNull(ServerCacheManager.getObject(originals.first().id)))
            val event = checkNotNull(interactions.opTrigger(player, clicked, InteractionOp.Op1))
            assertInstanceOf(LocEvents.Op1::class.java, event)
            var completion: Result<Unit>? = null
            val action: suspend () -> Unit = { f.events.publish(ProtectedAccess(player, coroutine, context), event) }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            assertTrue(messages.any { it.toString().contains("workmen") })
            assertFalse(guard.isBusy)
            assertNull(guard.activeCoroutine)
            assertEquals(0, genericOpens)
            originals.forEach { assertEquals(it, f.locs.findExact(it.coords, LocShape.WallStraight)) }

            val destination = if (gate == MuseumWorkman.MuseumGate) MuseumWorkman.UnloadPosition else CoordGrid(3293, 3428)
            val from = if (gate == MuseumWorkman.MuseumGate) CoordGrid(3264, 3440) else CoordGrid(3297, 3428)
            val workman = f.npc(MuseumWorkman.Loaded, from).apply { prepareJourney(destination) }
            script.operateGate(guard, gate)
            assertTrue(script.crossGate(workman, destination))
            workman.coords = if (gate == MuseumWorkman.MuseumGate) CoordGrid(3264, 3442) else CoordGrid(3294, 3428)
            repeat(3) { script.operateGate(guard, gate) }
            originals.forEach { assertEquals(it, f.locs.findExact(it.coords, LocShape.WallStraight)) }
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            coroutine.cancel()

            val talkEvent = checkNotNull(NpcInteractions(f.events).opTrigger(player, guard, InteractionOp.Op1))
            val talk = GameCoroutine("museum-guard-talk")
            player.activeCoroutine = talk
            val dialogue: suspend () -> Unit = { f.events.publish(ProtectedAccess(player, talk, context), talkEvent) }
            dialogue.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) {
                    if (result.exceptionOrNull() !is CancellationException) result.getOrThrow()
                }
            })
            assertTrue(talk.isAwaiting(ResumePauseButtonInput::class))
            assertFalse(guard.isBusy)
            assertNull(guard.activeCoroutine)
            assertTrue(messages.count { it.toString().contains("workmen") } >= 2)
            talk.cancel()
        }
    }

    private data class GateLeg(val gate: MuseumWorkman.Gate, val type: String, val from: CoordGrid, val destination: CoordGrid, val clear: CoordGrid)

    private fun closedLeaves(gate: MuseumWorkman.Gate): List<LocInfo> = if (gate == MuseumWorkman.MuseumGate) {
        listOf(wall("loc.fai_varrock_museum_door_closed_r".asRSCM(), 3264, 3441, 1),
            wall("loc.fai_varrock_museum_door_closed_l".asRSCM(), 3265, 3441, 1))
    } else {
        listOf(wall("loc.vm_fencegate_r".asRSCM(), 3296, 3429, 0), wall("loc.vm_fencegate_l".asRSCM(), 3296, 3428, 0))
    }

    private class RecordingClient(private val messages: MutableList<Any>) : Client<Any, Any> {
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    @Test fun `workman completes both journey legs using movement and changes his load at the endpoints`() {
        val f = AmbientNpcTest.Fixture()
        for (x in 3256..3370) for (z in 3400..3456) f.collision.allocateIfAbsent(x, z, 0)
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val workman = f.npc(MuseumWorkman.Empty, MuseumWorkman.Spawn)
        val loader = f.npc("npc.vm_digworkman_loader", MuseumWorkman.LoaderPosition)
        val tick = WorkmanTick(f)
        var loadedAt: CoordGrid? = null
        var unloadedAt: CoordGrid? = null
        repeat(500) {
            val before = workman.coords
            val beforeType = workman.visType.id
            tick.process()
            assertTrue(kotlin.math.abs(before.x - workman.coords.x) <= 1 && kotlin.math.abs(before.z - workman.coords.z) <= 1)
            if (beforeType != workman.visType.id) {
                if (workman.visType.id == MuseumWorkman.Loaded.asRSCM()) loadedAt = workman.coords
                else unloadedAt = workman.coords
            }
        }
        assertEquals(MuseumWorkman.ToDigsite.last(), loadedAt)
        assertEquals(MuseumWorkman.ToMuseum.last(), unloadedAt)
        assertTrue("seq.vm_wheelbarrow_loader".asRSCM() in f.info(loader).sequences)
        assertTrue("seq.vm_patrol_wheelbarrow_dumping_rocks".asRSCM() in f.info(workman).sequences)
    }

    @Test fun `unloading ends at the clear dock and explicitly releases the long final animation frame`() {
        val f = AmbientNpcTest.Fixture()
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val unload = CoordGrid(3264, 3444)
        val workman = f.npc(MuseumWorkman.Loaded, unload).apply { prepareJourney(unload) }
        f.collision.add(3264, 3446, 0, CollisionFlag.LOC)
        f.collision.add(3265, 3446, 0, CollisionFlag.LOC)
        script.travel(workman)
        assertEquals(unload, workman.coords)
        assertFalse(MuseumWorkman.Unload.asRSCM() in f.info(workman).sequences)
        assertNull(workman.pendingExactMove)
        val animationTicks = checkNotNull(ServerCacheManager.getAnim(MuseumWorkman.Unload.asRSCM())).tickDuration
        val tick = WorkmanTick(f)
        tick.process()
        assertFalse(MuseumWorkman.Unload.asRSCM() in f.info(workman).sequences)
        assertEquals(unload, workman.coords)
        tick.process()
        assertEquals(MuseumWorkman.Unload.asRSCM(), f.info(workman).sequences.last())
        assertEquals(unload, workman.coords)
        repeat(animationTicks) { tick.process(); assertTrue(workman.isVisType(MuseumWorkman.Loaded)) }
        tick.process()
        assertTrue(workman.isVisType(MuseumWorkman.Empty))
        assertEquals(-1, f.info(workman).sequences.last())
        tick.process()
        assertEquals(unload.translate(0, -1), workman.coords)
        assertNotEquals(0, f.collision[3264, 3446, 0] and CollisionFlag.LOC)
        assertNotEquals(0, f.collision[3265, 3446, 0] and CollisionFlag.LOC)
    }

    @Test fun `loader picks up toward the rocks and throws toward the stopped wheelbarrow`() {
        val f = AmbientNpcTest.Fixture()
        for (x in 3256..3370) for (z in 3400..3456) f.collision.allocateIfAbsent(x, z, 0)
        val script = MuseumWorkman(f.npcs, f.locs, f.world, RouteFactory(f.collision), f.collision)
        with(script) { f.context.startup() }
        val loader = f.npc(MuseumWorkman.Loader, MuseumWorkman.LoaderPosition)
        val protocol = LoaderInfo()
        loader.infoProtocol = protocol
        val workman = f.npc(MuseumWorkman.Empty, MuseumWorkman.Spawn)
        val tick = WorkmanTick(f)
        val throwing = MuseumWorkman.LoaderThrow.asRSCM()
        val throwTicks = checkNotNull(ServerCacheManager.getAnim(throwing)).tickDuration
        var budget = 120
        while (throwing !in protocol.sequences && budget-- > 0) tick.process()
        assertTrue(throwing in protocol.sequences)
        assertEquals(CoordGrid(3358, 3414), workman.coords)
        assertTrue(workman.routeDestination.isEmpty())
        assertEquals(loader.calculateAngle(MuseumWorkmanRoute.rocks, 1, 1), protocol.angle)
        tick.process()
        assertEquals(loader.calculateAngle(MuseumWorkmanRoute.rocks, 1, 1), protocol.angle)
        tick.process()
        assertEquals(throwing, protocol.sequences.last())
        assertEquals(loader.calculateAngle(workman.coords, 1, 1), protocol.angle)
        repeat(throwTicks - 2) {
            tick.process()
            assertEquals(CoordGrid(3358, 3414), workman.coords)
            assertTrue(workman.isVisType(MuseumWorkman.Empty))
        }
        tick.process()
        assertTrue(workman.isVisType(MuseumWorkman.Loaded))
        assertEquals(CoordGrid(3358, 3414), workman.coords)
        assertEquals(loader.calculateAngle(MuseumWorkmanRoute.rocks, 1, 1), protocol.angle)
        assertEquals(1, protocol.sequences.count { it == throwing })
        assertFalse("seq.vm_wheelbarrow_loader_ready".asRSCM() in protocol.sequences)
        tick.process()
        assertNotEquals(CoordGrid(3358, 3414), workman.coords)
    }

    private fun Npc.prepareJourney(destination: CoordGrid) {
        spawnCoords = MuseumWorkman.Spawn
        val route = if (isVisType(MuseumWorkman.Loaded)) MuseumWorkman.ToMuseum else MuseumWorkman.ToDigsite
        vars["varn.museum_workman_waypoint"] = route.indexOf(destination) + 1
    }

    private class LoaderInfo : NpcInfoProtocol by NoopNpcInfo {
        val sequences = mutableListOf<Int>()
        var angle: Int? = null
        override fun setSequence(seq: Int, delay: Int) { sequences += seq }
        override fun setFaceAngle(angle: Int, instant: Boolean) { this.angle = angle }
    }

    private fun AmbientNpcTest.Fixture.putMapLoc(loc: LocInfo) {
        storage.mapLocs[ZoneKey.from(loc.coords), LocZoneKey(ZoneGrid.from(loc.coords), loc.layer)] = loc.entity
        locs.add(loc, Int.MAX_VALUE)
    }

    private fun wall(id: Int, x: Int, z: Int, angle: Int) = LocInfo(0, CoordGrid(x, z), LocEntity(id, 0, angle))

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private fun mappedSpawn(symbol: String): CoordGrid {
            val spawns = listOf("kingdom_of_misthalin_region.toml", "varrock.toml")
                .joinToString("\n") { Files.readString(Path.of(".data/raw-cache/map/npcs", it)) }
            val coords = checkNotNull(Regex("npc = \"${Regex.escape(symbol)}\"\\s+coords = \"([^\"]+)\"").find(spawns))
                .groupValues[1].split('_').map(String::toInt)
            return CoordGrid(coords[0], coords[1], coords[2], coords[3], coords[4])
        }
    }
}
