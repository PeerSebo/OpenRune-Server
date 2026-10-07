package org.rsmod.content.generic.locs.edgeville

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.player.PlayerFaceSquareProcessor
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.signpost.SignpostScript
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
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
import org.rsmod.game.map.Direction
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EdgevilleSceneryDispatchTest {
    @Test fun `lever pull faces its wall and changes to the native down model before restoring`() {
        val levers = listOf(
            "loc.edgeville_wildy_lever" to EdgevilleSceneryScript.EdgevilleLever,
            "loc.wildinlever" to EdgevilleSceneryScript.ArdougneLever,
            "loc.wildoutlever" to EdgevilleSceneryScript.KeepLever,
        )
        for ((symbol, coords) in levers) for (arriving in listOf(false, true)) {
            val f = Fixture()
            f.player.coords = coords
            f.player.members = true
            f.player.faceAngle = Direction.East.angle
            if (arriving) f.player.lastMovement = f.player.currentMapClock
            f.set("varbit.wilderness_lever_blockwarning_standard", 1)
            val shape = if (symbol == "loc.wildoutlever") 5 else 4
            f.place(symbol, coords, shape)
            f.click(symbol, coords, shape = shape)
            assertEquals(Direction.West.angle, f.player.pendingFaceAngle.intValue, symbol)
            PlayerFaceSquareProcessor().process(f.player)
            assertEquals(Direction.West.angle, f.player.faceAngle, symbol)
            assertEquals("seq.human_leverdown".asRSCM(), f.player.pendingSequence.id, symbol)
            val changes = f.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<LocAddChangeV2>()
            assertEquals(1, changes.size, symbol)
            assertEquals("loc.magearena_lever_down".asRSCM(), changes.single().id, symbol)
            assertEquals(shape, changes.single().shape, symbol)
            assertEquals(coords, f.player.coords, "Lever flips before teleporting")
            f.tick()
            assertNotNull(f.registry.findType(coords, "loc.magearena_lever_down".asRSCM()))
            f.tick()
            assertNotNull(f.registry.findType(coords, symbol.asRSCM()), "Original lever and its actions return")
        }
    }

    @Test fun `return lever resolves native multiloc and lands in Ardougne without a diary`() {
        val f = Fixture()
        f.player.coords = EdgevilleSceneryScript.KeepLanding
        f.click("loc.wildoutlever", EdgevilleSceneryScript.KeepLever)
        f.tick()
        assertEquals(714, f.player.pendingSequence.id)
        f.player.activeCoroutine = null
        repeat(4) { f.tick() }
        assertEquals(EdgevilleSceneryScript.ArdougneLanding, f.player.coords)
        assertEquals(715, f.player.pendingSequence.id)
        assertTrue(f.player.queueList.isEmpty)
    }

    @Test fun `area denial before casting and at landing prevents teleport`() {
        val denied = Fixture().apply { denial = "A teleport block has been cast on you." }
        denied.player.coords = EdgevilleSceneryScript.KeepLanding
        denied.click("loc.wildoutlever", EdgevilleSceneryScript.KeepLever)
        assertTrue(denied.player.queueList.isEmpty)
        assertTrue(denied.client.messages.any { "teleport block" in it.toString() })
        assertTrue(denied.updates.updatedZones.isEmpty())

        val f = Fixture()
        f.player.coords = EdgevilleSceneryScript.KeepLanding
        f.click("loc.wildoutlever", EdgevilleSceneryScript.KeepLever)
        f.tick()
        f.denial = "A teleport block has been cast on you."
        repeat(4) { f.tick() }
        assertEquals(EdgevilleSceneryScript.KeepLanding, f.player.coords)
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        assertTrue(f.checkedTypes.all { it == TeleportType.Lever })
    }

    @Test fun `diary alternate option and swapped primary option both return to Edgeville`() {
        for ((visible, op) in listOf(1 to InteractionOp.Op2, 2 to InteractionOp.Op1)) {
            val f = Fixture()
            f.player.coords = EdgevilleSceneryScript.KeepLanding
            f.set("varbit.wilderness_diary_easy_complete", 1)
            f.set("varbit.wildoutlever_vis", visible)
            f.click("loc.wildoutlever", EdgevilleSceneryScript.KeepLever, op)
            repeat(5) { f.tick() }
            assertEquals(EdgevilleSceneryScript.EdgevilleLanding, f.player.coords)
        }
    }

    @Test fun `forged diary option does not grant Edgeville travel without the diary`() {
        val f = Fixture()
        f.player.coords = EdgevilleSceneryScript.KeepLanding
        f.set("varbit.wildoutlever_vis", 1)
        f.click("loc.wildoutlever", EdgevilleSceneryScript.KeepLever, InteractionOp.Op2)
        repeat(5) { f.tick() }
        assertEquals(EdgevilleSceneryScript.KeepLanding, f.player.coords)
        assertTrue(f.client.messages.any { "easy Wilderness Diary" in it.toString() })
    }

    @Test fun `Edgeville coffin opening and closing preserve placement shape angle and emit sounds`() {
        for (coords in EdgevilleSceneryScript.Coffins) for (angle in 0..3) {
            val f = Fixture()
            f.player.coords = coords.translate(1, 0)
            f.click("loc.coffin", coords, angle = angle)
            val opened = checkNotNull(f.registry.findType(coords, "loc.coffin_open".asRSCM()))
            assertEquals(angle, opened.angle.id)
            assertEquals(10, opened.shape.id)
            f.click("loc.coffin_open", coords, angle = angle)
            val closed = checkNotNull(f.registry.findType(coords, "loc.coffin".asRSCM()))
            assertEquals(angle, closed.angle.id)
            assertTrue(f.client.messages.any { "SynthSound(id=54," in it.toString() })
            assertTrue(f.client.messages.any { "SynthSound(id=53," in it.toString() })
        }
    }

    @Test fun `unrelated coffins stay untouched and southern Varrock signpost has approach and click handlers`() {
        val f = Fixture()
        val unrelated = CoordGrid(3201, 3200)
        f.click("loc.coffin", unrelated)
        assertNull(f.registry.findType(unrelated, "loc.coffin_open".asRSCM()))
        assertTrue(f.client.messages.isEmpty())
        assertTrue(f.events.contains(LocEvents.Ap1::class.java, "loc.fai_varrock_signpost_1".asRSCM()))
        assertTrue(f.events.contains(LocEvents.Op1::class.java, "loc.fai_varrock_signpost_1".asRSCM()))
    }

    private class Fixture {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100; processedMapClock = 100
        }
        var denial: String? = null
        val checkedTypes = mutableListOf<TeleportType>()
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, type, _ ->
            checkedTypes += type
            denial
        }))
        private val collision = CollisionFlagMap().apply {
            for (coords in EdgevilleSceneryScript.Coffins + listOf(EdgevilleSceneryScript.KeepLanding,
                EdgevilleSceneryScript.EdgevilleLanding, EdgevilleSceneryScript.ArdougneLanding, player.coords)) {
                for (dx in -1..1) for (dz in -1..1) allocateIfAbsent(coords.x + dx, coords.z + dz, coords.level)
            }
        }
        val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val clock = MapClock(100)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, events), ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val registry = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        private val repo = LocRepository(clock, registry, regions)
        private val areas = AreaChecker(regions, AreaIndex())
        private val world = WorldRepository(updates)
        private val accessContext = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision }, getWorldRepo = { world },
            getAreaChecker = { areas }, getTeleportValidator = { validator },
        )

        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        private val queues = PlayerQueueProcessor(events) { target, event ->
            ProtectedAccessLauncher.withProtectedAccess(target, accessContext) { events.publish(this, event) }
        }
        private val interactions = LocInteractions(BoundValidator(collision), events)
        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(EdgevilleSceneryScript(repo, validator, areas)) { context.startup() }
            with(SignpostScript()) { context.startup() }
        }
        fun set(name: String, value: Int) = VarPlayerIntMapSetter.set(player, name, value)
        fun place(symbol: String, coords: CoordGrid, shape: Int) {
            val layer = if (shape in 4..8) 1 else 2
            storage.mapLocs[ZoneKey.from(coords), LocZoneKey(ZoneGrid.from(coords), layer)] = LocEntity(symbol.asRSCM(), shape, 0)
        }
        fun click(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1, angle: Int = 0, shape: Int = 10) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(if (shape in 4..8) 1 else 2, coords, LocEntity(type.id, shape, angle)), type)
            val event = checkNotNull(interactions.opTrigger(player, loc, op))
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, accessContext) {
                assertTrue(events.publish(this, event))
            })
        }
        fun tick() {
            clock.tick()
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.pendingSequence = EntitySeq.NULL
            player.activeCoroutine?.advance()
            queues.process(player)
            val durations = LocRepository::class.java.declaredMethods.single { it.name.startsWith("processDurations") }
            durations.isAccessible = true
            durations.invoke(repo)
        }
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
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
