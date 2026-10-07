package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import java.nio.file.Files
import java.nio.file.Path
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.npc.AiTimerProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.game.process.npc.mode.NpcModeProcessor
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.StepFactory
import org.rsmod.content.generic.npcs.ducks.Ducks
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DucklingRegressionTest {
    @Test
    fun `mapped ducklings swim from their actual ponds with native terrain and scenery collision`() {
        assertEquals(7, nativeSpawns.size, "Exercise all duckling spawns in the four selected map files")
        assertEquals(DUCKLINGS.toSet(), nativeSpawns.map { it.first }.toSet())
        val steps = StepFactory(nativeCollision)
        for ((symbol, start) in nativeSpawns) {
            val random = FixedRandom(wander = true)
            val f = Fixture(symbol, random, start, nativeCollision)
            if (guildPool(start)) {
                assertEquals(MoveRestrict.PassThru, f.duckling.moveRestrict)
                assertEquals(0, nativeCollision[start.x, start.z, start.level] and CollisionFlag.BLOCK_WALK,
                    "The native Guild pool is ordinary floor enclosed by invisible walls")
            } else {
                assertEquals(MoveRestrict.Blocked, f.duckling.moveRestrict)
                assertTrue(nativeCollision[start.x, start.z, start.level] and CollisionFlag.BLOCK_WALK != 0,
                    "The mapped $symbol spawn must be on water: $start")
            }
            val destination = (-1..1).flatMap { dx -> (-1..1).map { dz -> start.translate(dx, dz) } }
                .filter { it != start }
                .firstOrNull { steps.validated(f.duckling, start, it, checkNotNull(f.duckling.collisionStrategy)) == it }
            assertTrue(destination != null, "No native swimming exit for $symbol at $start")
            random.destination = checkNotNull(destination)
            f.tick()
            assertEquals(destination, f.duckling.coords, "The real timer and movement must leave $start")
        }
    }

    @Test
    fun `all native Guild pool edges confine ducks and ducklings without a blocked floor`() {
        for (symbol in DUCKLINGS + listOf("npc.duck", "npc.duck_female")) {
            for (x in 1225..1226) for (z in 3727..3732) {
                val start = CoordGrid(x, z, 0)
                val f = Fixture(symbol, start = start, nativeFlags = nativeCollision)
                assertEquals(MoveRestrict.PassThru, f.duckling.moveRestrict)
                val movement = NpcMovementProcessor(nativeCollision, StepFactory(nativeCollision), EventBus())
                for (dx in -1..1) for (dz in -1..1) {
                    val outside = start.translate(dx, dz)
                    if (guildPool(outside)) continue
                    f.duckling.coords = start
                    f.duckling.walk(outside)
                    repeat(3) { movement.process(f.duckling) }
                    assertTrue(guildPool(f.duckling.coords), "$symbol escaped the pool from $start toward $outside")
                }
            }
        }
    }

    @Test
    fun `pool movement is not granted to other floors or retained after displacement`() {
        val inside = CoordGrid(1226, 3729, 0)
        val f = Fixture(start = inside, nativeFlags = nativeCollision)
        assertEquals(MoveRestrict.PassThru, f.duckling.moveRestrict)
        val upstairs = f.add("npc.duckling", inside.copy(level = 1))
        assertEquals(MoveRestrict.Blocked, upstairs.moveRestrict)
        f.relocate(f.duckling, inside.translate(2, 0))
        f.tick()
        assertEquals(MoveRestrict.Blocked, f.duckling.moveRestrict)
    }

    @Test
    fun `both native duckling variants have swimming and timer policies packed`() {
        for (symbol in DUCKLINGS) {
            val npc = Npc(symbol, START)
            assertEquals(MoveRestrict.Blocked, npc.moveRestrict, symbol)
            assertEquals(NpcMode.None, npc.defaultMode, symbol)
            assertEquals(1, npc.type.timer, symbol)
            assertEquals("content.duckling".asRSCM(), npc.type.contentGroup, symbol)
            assertTrue(npc.wanderRange > 0, symbol)
            assertNull(npc.huntMode, "An undefined hunt must not control $symbol")
        }
    }

    @Test
    fun `both ducklings follow a moving mother one water tile per tick`() {
        for (symbol in DUCKLINGS) {
            val f = Fixture(symbol)
            val mother = f.add("npc.duck_female", START.translate(4, 0))
            f.tick(3)
            assertEquals(START.translate(3, 0), f.duckling.coords)
            assertEquals(mother.slotId, f.duckling.faceEntity.npcSlot)
            f.tick()
            assertEquals(START.translate(3, 0), f.duckling.coords, "Stop beside the mother")
            f.relocate(mother, START.translate(5, 0))
            f.tick()
            assertEquals(START.translate(4, 0), f.duckling.coords)
            assertTrue(f.info.sequences.isEmpty(), "The native swim animation must not be restarted")
        }
    }

    @Test
    fun `following cannot cross dry land to another piece of water`() {
        val f = Fixture()
        f.add("npc.duck_female", START.translate(4, 0))
        for (z in START.z - 6..START.z + 6) f.flags[START.x + 1, z, 0] = 0
        f.tick(8)
        assertEquals(START, f.duckling.coords)
    }

    @Test
    fun `an orphan can swim and rejects a land destination`() {
        val random = FixedRandom(wander = true, destination = START.translate(4, 0))
        val f = Fixture(random = random)
        f.tick()
        assertEquals(START.translate(1, 0), f.duckling.coords)
        random.destination = START.translate(1, 8)
        f.duckling.abortRoute()
        f.tick(3)
        assertEquals(START.translate(1, 0), f.duckling.coords)
    }

    @Test
    fun `duckling Eep uses native baby sound two ticks after the mothers call`() {
        for (symbol in DUCKLINGS) {
            val f = Fixture(symbol)
            val motherInfo = RecordingInfo()
            val mother = f.add("npc.duck_female", START.translate(1, 0)).apply { infoProtocol = motherInfo }
            f.tick(49)
            assertTrue(f.sounds().isEmpty())
            f.tick()
            assertEquals(listOf("Quack?"), motherInfo.messages)
            assertEquals(listOf(413), f.sounds().map { it.id })
            assertEquals(mother.x and 7, f.sounds().single().xInZone)
            f.tick()
            assertTrue(f.info.messages.isEmpty())
            f.tick()
            assertEquals(listOf("Eep!"), f.info.messages)
            assertEquals(listOf(413, 412), f.sounds().map { it.id })
            val baby = f.sounds().last()
            assertEquals(f.duckling.x and 7, baby.xInZone)
            assertEquals(1, baby.loops)
            assertEquals(5, baby.range)
        }
    }

    @Test
    fun `dead hidden removed and distant mothers are abandoned`() {
        for (invalidate in listOf<(Fixture, Npc) -> Unit>(
            { _, mother -> mother.hitpoints = 0 },
            { f, mother -> f.registry.hide(mother) },
            { f, mother -> f.registry.del(mother) },
            { f, mother -> f.relocate(mother, START.translate(20, 0)) },
        )) {
            val f = Fixture()
            val mother = f.add("npc.duck_female", START.translate(4, 0))
            f.tick()
            val last = f.duckling.coords
            invalidate(f, mother)
            f.tick(3)
            assertEquals(last, f.duckling.coords)
            assertFalse(f.duckling.isFacingNpc)
        }
    }

    @Test
    fun `mothers on another floor cannot attract the duckling`() {
        val f = Fixture()
        f.add("npc.duck_female", START.translate(1, 0).copy(level = 1))
        f.tick(3)
        assertEquals(START, f.duckling.coords)
        assertFalse(f.duckling.isFacingNpc)
    }

    @Test
    fun `mourning uses the baby sound and swims away on water`() {
        val f = Fixture(random = FixedRandom(destination = START.translate(-4, 0)))
        f.add("npc.duck_female", START.translate(1, 0))
        f.tick()
        f.duckling.queue("queue.generic_queue2", 1)
        f.tick()
        assertEquals(listOf("Eep!"), f.info.messages)
        assertEquals(listOf(412), f.sounds().map { it.id })
        assertEquals(START.translate(-1, 0), f.duckling.coords)
        f.tick(2)
        assertEquals(START.translate(-3, 0), f.duckling.coords)
    }

    private class FixedRandom(
        val wander: Boolean = false,
        var destination: CoordGrid = START.translate(4, 0),
    ) : GameRandom by DefaultGameRandom(1) {
        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive
        override fun of(rangeInclusive: IntRange): Int = rangeInclusive.first
        override fun randomBoolean(maxExclusive: Int): Boolean = wander
        override fun of(coords: CoordGrid, radius: Int): CoordGrid = destination
    }

    private class Fixture(
        symbol: String = DUCKLINGS.first(),
        random: GameRandom = FixedRandom(),
        start: CoordGrid = START,
        nativeFlags: CollisionFlagMap? = null,
    ) {
        val flags = nativeFlags ?: CollisionFlagMap().apply {
            for (x in START.x - 40..START.x + 40) {
                for (z in START.z - 40..START.z + 40) this[x, z, 0] = 0
            }
            for (x in START.x - 6..START.x + 6) {
                for (z in START.z - 6..START.z + 6) this[x, z, 0] = CollisionFlag.BLOCK_WALK
            }
        }
        val events = EventBus()
        val registry = NpcRegistry(NpcList(), flags, events)
        private val updates = ZoneUpdateMap()
        private val search = NpcSearch(Hunt(RayCastValidator(flags), unused(), registry, ObjRegistry(updates), unused()))
        private val script = Ducks(random, unused<NpcDeath>(), search, WorldRepository(updates), flags)
        private val timers = AiTimerProcessor(events)
        private val queues = NpcQueueProcessor(events,
            StandardNpcAccessLauncher(StandardNpcAccessContextFactory(random,
                NpcHitModifier { error("Duckling movement and calls must not modify combat hits") },
                NpcHitProcessor { error("Duckling movement and calls must not process combat hits") })))

        private val modes = unused<NpcModeProcessor>()
        private val movement = NpcMovementProcessor(flags, StepFactory(flags), events)

        init { with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() } }

        val info = RecordingInfo()
        val duckling = add(symbol, start).apply { infoProtocol = info }
        private var cycle = 100

        fun add(symbol: String, coords: CoordGrid): Npc = Npc(symbol, coords).also { registry.add(it) }

        fun relocate(npc: Npc, destination: CoordGrid) {
            val previousZone = ZoneKey.from(npc.coords)
            npc.coords = destination
            val nextZone = ZoneKey.from(destination)
            if (previousZone != nextZone) registry.change(npc, previousZone, nextZone)
            npc.lastProcessedZone = nextZone
        }

        fun tick(count: Int = 1) = repeat(count) {
            duckling.currentMapClock = ++cycle
            duckling.processedMapClock = cycle
            duckling.previousCoords = duckling.coords
            val zone = ZoneKey.from(duckling.coords)
            timers.process(duckling)
            queues.process(duckling)
            modes.process(duckling)
            movement.process(duckling)
            val nextZone = ZoneKey.from(duckling.coords)
            if (zone != nextZone) registry.change(duckling, zone, nextZone)
            duckling.lastProcessedZone = nextZone
        }

        fun sounds(): List<SoundArea> = updates.updatedZones.values.flatMap { it.protList }.filterIsInstance<SoundArea>()
    }

    private class RecordingInfo : NpcInfoProtocol by NoopNpcInfo {
        val messages = mutableListOf<String>()
        val sequences = mutableListOf<Int>()
        override fun setSay(text: String) { messages += text }
        override fun setSequence(seq: Int, delay: Int) { sequences += seq }
    }

    companion object {
        private val START = CoordGrid(3202, 3202, 0)
        private val DUCKLINGS = listOf("npc.duckling", "npc.duck_update_ducklings")
        private val nativeCollision = CollisionFlagMap()
        private fun guildPool(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 1225..1226 && coords.z in 3727..3732
        private val nativeSpawns = listOf(
            "lumbridge.toml", "falador.toml", "kingdom_of_misthalin_region.toml", "kourend.toml",
        ).flatMap { file ->
            val source = Files.readString(Path.of(".data/raw-cache/map/npcs", file))
            Regex("npc = \"(npc\\.duckling|npc\\.duck_update_ducklings)\"\\s+coords = \"([^\"]+)\"")
                .findAll(source).map { match ->
                    val c = match.groupValues[2].split('_').map(String::toInt)
                    match.groupValues[1] to CoordGrid(c[0], c[1], c[2], c[3], c[4])
                }.toList()
        }

        @JvmStatic @BeforeAll fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                val builder = GameMapBuilder()
                for ((mx, mz) in nativeSpawns.map { it.second.mx to it.second.mz }.toSet()) {
                    val group = (mx shl 8) or mz
                    val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                    val scenery = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                    for (level in 0..3) for (x in mx * 64 until (mx + 1) * 64 step 8)
                        for (z in mz * 64 until (mz + 1) * 64 step 8) nativeCollision.allocateIfAbsent(x, z, level)
                    GameMapDecoder.putMaps(nativeCollision, MapSquareKey(mx, mz), tiles)
                    GameMapDecoder.putLocs(builder, nativeCollision, MapSquareKey(mx, mz), tiles, scenery)
                }
            } finally { cache.close() }
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
