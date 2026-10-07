package org.rsmod.content.generic.npcs.ambient

import dev.openrune.ServerCacheManager
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.definition.codec.SequenceCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import java.nio.file.Path
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.npc.AiTimerProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.random.DefaultGameRandom
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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.Direction
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class AmbientNpcTest {
    @Test fun `drill formation responds together through native timers without walking`() {
        val f = Fixture()
        val soldiers = GuardTraining.Formation.map { f.npc(GuardTraining.DrillingSoldier, it) }
        val outside = f.npc(GuardTraining.DrillingSoldier, CoordGrid(2893, 3543))
        val sergeant = f.npc(GuardTraining.DrillSergeant, GuardTraining.DrillPosition)
        repeat(12) { f.timers.process(sergeant) }
        assertEquals(listOf("Squad... ATTENTION!"), f.info(sergeant).messages)
        for (soldier in soldiers) {
            assertEquals(listOf("seq.human_turnonspot".asRSCM()), f.info(soldier).sequences)
            soldier.walk(soldier.coords.translate(1, 0))
            f.movement.process(soldier)
            assertEquals(soldier.spawnCoords, soldier.coords)
        }
        assertTrue(f.info(outside).sequences.isEmpty())
        repeat(12) { f.timers.process(sergeant) }
        assertEquals("Left turn!", f.info(sergeant).messages.last())
    }

    @Test fun `Varrock training emits actor dummy and area sound updates together`() {
        val f = Fixture()
        val npc = f.npc(GuardTraining.Trainees.first(), CoordGrid(3209, 3490, 2))
        val dummy = LocInfo(2, CoordGrid(3209, 3489, 2), LocEntity(GuardTraining.Dummy.asRSCM(), 10, 2))
        f.storage.mapLocs[ZoneKey.from(dummy.coords), LocZoneKey(ZoneGrid.from(dummy.coords), dummy.layer)] = dummy.entity
        repeat(8) { f.timers.process(npc) }
        assertTrue(f.info(npc).sequences.isEmpty())
        f.timers.process(npc)
        assertEquals(listOf("seq.human_sword_stab".asRSCM()), f.info(npc).sequences)
        assertTrue(npc.aiTimer in 9..12)
        val packets = f.updates.updatedZones.values.flatMap { it.toList() }
        assertEquals(1, packets.filterIsInstance<LocAnim>().size)
        assertEquals(1, packets.filterIsInstance<SoundArea>().size)
        assertEquals(npc.spawnCoords, npc.coords)
    }

    @Test fun `Burthorpe soldiers face the dummy east of each native training position`() {
        val f = Fixture()
        for (coords in GuardTraining.TrainingPositions) {
            f.loc(GuardTraining.SwordDummy, coords.translate(1, 0))
            val soldier = f.npc(GuardTraining.TrainingSoldier, coords)
            assertEquals(Direction.East, soldier.respawnDir)
            repeat(8) { f.timers.process(soldier) }
            assertEquals(coords.translate(1, 0), soldier.pendingFaceSquare)
            assertEquals(listOf("seq.human_sword_slash".asRSCM()), f.info(soldier).sequences)
            assertEquals(coords, soldier.coords)
        }
    }

    @Test fun `Varrock trainees use the compatible sword stab sequence`() {
        for (symbol in GuardTraining.Trainees) {
            val f = Fixture()
            val npc = f.npc(symbol, CoordGrid(3211, 3490, 2))
            f.loc(GuardTraining.Dummy, npc.coords.translate(0, -1))
            repeat(9) { f.timers.process(npc) }
            assertEquals(listOf("seq.human_sword_stab".asRSCM()), f.info(npc).sequences, symbol)
            assertEquals(npc.coords.translate(0, -1), npc.pendingFaceSquare, symbol)
            val packets = f.updates.updatedZones.values.flatMap { it.toList() }
            assertEquals(1, packets.filterIsInstance<LocAnim>().size, symbol)
            assertEquals(1, packets.filterIsInstance<SoundArea>().size, symbol)
        }
    }

    @Test fun `female trainees have a stable native human pose in the served cache and server definitions`() {
        val ready = "seq.varrock_female_trainee_ready".asRSCM()
        for (cacheName in listOf("LIVE", "SERVER")) {
            val cache = Cache.load(Path.of(".data/cache/$cacheName"))
            try {
                val codec = SequenceCodec(240)
                val original = codec.loadData("seq.human_ready".asRSCM(), checkNotNull(cache.data(2, 12, "seq.human_ready".asRSCM())))
                val stable = codec.loadData(ready, checkNotNull(cache.data(2, 12, ready)))
                assertEquals(listOf(checkNotNull(original.frameIDs).first()), stable.frameIDs)
                assertEquals(listOf(checkNotNull(original.frameDelays).first()), stable.frameDelays)
                assertEquals(1, stable.frameStep)
                assertEquals(original.skeletalId, stable.skeletalId)
                assertEquals(original.interleaveLeave, stable.interleaveLeave)
                assertEquals(original.verticalOffset, stable.verticalOffset)
                for (symbol in GuardTraining.Trainees) {
                    val id = symbol.asRSCM()
                    val native = NPCCodec(240).loadData(id, checkNotNull(cache.data(2, 9, id)))
                    val expected = if (symbol.contains("training02_f")) ready else "seq.human_ready".asRSCM()
                    assertEquals(expected, native.standAnim, "$cacheName $symbol")
                    assertEquals(expected, checkNotNull(ServerCacheManager.getNpc(id)).standAnim, symbol)
                }
            } finally {
                cache.close()
            }
        }
    }

    @Test fun `female trainee idle correction preserves their native body and stationary training behavior`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for ((trainee, guard) in listOf(
                "npc.fai_varrock_guard_training02_f" to "npc.fai_varrock_guard02_f",
                "npc.fai_varrock_guard_training02_f_variant01" to "npc.fai_varrock_guard02_f_variant01",
            )) {
                val id = trainee.asRSCM()
                val guardId = guard.asRSCM()
                val native = NPCCodec(240).loadData(id, checkNotNull(cache.data(2, 9, id)))
                val model = NPCCodec(240).loadData(guardId, checkNotNull(cache.data(2, 9, guardId)))
                assertEquals(model.models, native.models, trainee)
                assertEquals(model.originalColours, native.originalColours, trainee)
                assertEquals(model.modifiedColours, native.modifiedColours, trainee)
                assertEquals(model.widthScale, native.widthScale, trainee)
                assertEquals(model.heightScale, native.heightScale, trainee)
                assertEquals(model.walkAnim, native.walkAnim, trainee)
                val npc = Npc(trainee, CoordGrid(3211, 3490, 2))
                assertEquals(MoveRestrict.NoMove, npc.moveRestrict, trainee)
                assertEquals(NpcMode.None, npc.defaultMode, trainee)
                assertEquals(0, npc.wanderRange, trainee)
                assertEquals(9, npc.aiTimer, trainee)
                assertEquals(Direction.South, npc.respawnDir, trainee)
            }
        } finally {
            cache.close()
        }
    }

    @Test fun `Burthorpe training does not swing at missing dummies or interrupt busy soldiers`() {
        val f = Fixture()
        val soldier = f.npc(GuardTraining.TrainingSoldier, CoordGrid(2900, 3531))
        repeat(8) { f.timers.process(soldier) }
        assertTrue(f.info(soldier).sequences.isEmpty())
        f.loc(GuardTraining.SwordDummy, soldier.coords.translate(1, 0))
        soldier.hidden = true
        repeat(8) { f.timers.process(soldier) }
        assertTrue(f.info(soldier).sequences.isEmpty())
    }

    @Test fun `dummy sergeant addresses nearby trainees through his native timer`() {
        val f = Fixture()
        val soldiers = GuardTraining.TrainingPositions.map { f.npc(GuardTraining.TrainingSoldier, it) }
        val sergeant = f.npc(GuardTraining.TrainingSergeant, GuardTraining.TrainingSergeantPosition)
        repeat(34) { f.timers.process(sergeant) }
        assertTrue(f.info(sergeant).messages.isEmpty())
        f.timers.process(sergeant)
        assertTrue(f.info(sergeant).messages.single() in GuardTraining.TrainingInstructions)
        assertTrue(sergeant.aiTimer in 35..70)
        val respondent = soldiers.single { f.info(it).messages.isNotEmpty() }
        assertEquals(listOf("Yes sir!"), f.info(respondent).messages)
        assertEquals(respondent.coords, sergeant.pendingFaceSquare)
        assertEquals(GuardTraining.TrainingSergeantPosition, sergeant.coords)
        soldiers.forEach { it.hidden = true }
        repeat(sergeant.aiTimer) { f.timers.process(sergeant) }
        assertEquals(1, f.info(sergeant).messages.size)
    }

    @Test fun `seated Burthorpe soldiers keep their sitting sequence while facing the fire`() {
        val fire = CoordGrid(2893, 3532)
        for ((symbol, direction) in listOf(
            "npc.death_ig_solider_sitting1" to Direction.East,
            "npc.death_ig_solider_sitting2" to Direction.South,
            "npc.death_ig_solider_sitting3" to Direction.West,
        )) {
            val coords = fire.translate(-direction.xOff, -direction.zOff)
            val npc = Npc(symbol, coords)
            assertEquals(direction, npc.respawnDir, symbol)
            assertEquals("seq.sitting_ready".asRSCM(), npc.type.standAnim, symbol)
            assertEquals(MoveRestrict.NoMove, npc.moveRestrict, symbol)
            assertEquals(0, npc.wanderRange, symbol)
        }
    }

    @Test fun `captain paces and speaks every twenty ticks while busy trainees keep their action`() {
        val f = Fixture()
        val captain = f.npc(GuardTraining.Captain, CoordGrid(3213, 3491, 2))
        repeat(41) { f.timers.process(captain); f.movement.process(captain) }
        assertEquals(3, f.info(captain).messages.size)
        assertNotEquals(captain.spawnCoords, captain.coords)
        assertEquals(NpcMode.None, captain.defaultMode)
        val trainee = f.npc(GuardTraining.Trainees.first(), CoordGrid(3209, 3490, 2))
        trainee.hidden = true
        repeat(9) { f.timers.process(trainee) }
        assertTrue(f.info(trainee).sequences.isEmpty())
    }

    @Test fun `stationary actors retain their native ready sequences`() {
        for (symbol in listOf("npc.eadgar_druid_washing", "npc.vm_museum_guard2") + GuardTraining.Trainees) {
            val npc = Npc(symbol, CoordGrid(3200, 3200))
            assertEquals(MoveRestrict.NoMove, npc.moveRestrict, symbol)
            assertEquals(0, npc.wanderRange, symbol)
            assertEquals(NpcMode.None, npc.defaultMode, symbol)
        }
        assertEquals(619, Npc("npc.eadgar_druid_washing", CoordGrid(3200, 3200)).type.standAnim)
    }

    @Test fun `rooftop spectator direction is specific to each verified rooftop spawn`() {
        val f = Fixture()
        with(RooftopSpectatorFacing(f.npcs)) { f.context.startup() }
        for ((coords, direction) in RooftopSpectatorFacing.Directions) {
            val spectator = f.npc("npc.rooftops_bigredjapan", coords)
            assertEquals(direction, spectator.respawnDir)
            assertEquals(coords, spectator.coords)
        }
        val other = f.npc("npc.man", RooftopSpectatorFacing.Directions.keys.first())
        assertEquals(other.type.respawnDir, other.respawnDir)
    }

    internal class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val collision = CollisionFlagMap()
        val npcList = NpcList()
        val npcRegistry = NpcRegistry(npcList, collision, events)
        val npcs = NpcRepository(clock, npcRegistry, npcList)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val world = WorldRepository(updates)
        val timers = AiTimerProcessor(events)
        val movement = NpcMovementProcessor(collision, StepFactory(collision), events)

        init { with(GuardTraining(DefaultGameRandom(1), npcs, locs, world)) { context.startup() } }

        fun npc(symbol: String, coords: CoordGrid): Npc = Npc(symbol, coords).apply {
            for (x in coords.x - 8..coords.x + 8) for (z in coords.z - 8..coords.z + 8) collision.allocateIfAbsent(x, z, coords.level)
            infoProtocol = RecordingInfo()
            npcs.add(this, Int.MAX_VALUE)
        }

        fun info(npc: Npc) = npc.infoProtocol as RecordingInfo

        fun loc(symbol: String, coords: CoordGrid) {
            val loc = LocInfo(0, coords, LocEntity(symbol.asRSCM(), 10, 0))
            storage.mapLocs[ZoneKey.from(coords), LocZoneKey(ZoneGrid.from(coords), loc.layer)] = loc.entity
        }
    }

    internal class RecordingInfo : NpcInfoProtocol by NoopNpcInfo {
        val messages = mutableListOf<String>()
        val sequences = mutableListOf<Int>()
        override fun setSay(text: String) { messages += text }
        override fun setSequence(seq: Int, delay: Int) { sequences += seq }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
