package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.npc.AiTimerProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.StepFactory
import org.rsmod.content.generic.npcs.builders.TheGuns
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class StationaryNpcRegressionTest {
    @Test
    fun `fixed actors cannot take a step even if a walk request is supplied`() {
        assertEquals(70, fixedTypes.size)
        for (symbol in fixedTypes) {
            val npc = Npc("npc.$symbol", START)
            assertEquals(NpcMode.None, npc.defaultMode, symbol)
            assertEquals(0, npc.wanderRange, symbol)
            assertEquals(MoveRestrict.NoMove, npc.moveRestrict, symbol)
            npc.walk(START.translate(1, 0))
            movement().process(npc)
            assertEquals(START, npc.coords, symbol)
            assertTrue(npc.routeDestination.isEmpty(), symbol)
        }
    }

    @Test
    fun `mobile people and butterflies still wander`() {
        for (symbol in listOf("npc.man", "npc.butterfly_bluemorpo")) {
            val npc = Npc(symbol, START)
            assertEquals(NpcMode.Wander, npc.defaultMode, symbol)
            assertTrue(npc.wanderRange > 0, symbol)
            npc.walk(START.translate(1, 0))
            movement().process(npc)
            assertEquals(START.translate(1, 0), npc.coords, symbol)
        }
    }

    @Test
    fun `builders keep native actions and workout animation while being stationary`() {
        val blackEye = Npc("npc.tol_npc_builder01", START).type
        assertEquals("Talk-to", blackEye.actions.getOpOrNull(0))
        assertTrue((0 until 5).any { blackEye.actions.getOpOrNull(it) == "Pickpocket" })
        val guns = Npc("npc.tol_npc_builder04", START).type
        assertEquals("seq.tol_lazy_builder04".asRSCM(), guns.standAnim)
        val press = checkNotNull(ServerCacheManager.getAnim(guns.standAnim))
        assertEquals(86, press.totalDelay)
        assertEquals(press.tickDuration, guns.timer)
        assertEquals(3, guns.timer)
    }

    @Test
    fun `Guns counts on every third tick without restarting his native animation`() {
        val f = WorkoutFixture()
        f.tick(2)
        assertTrue(f.info.messages.isEmpty())
        f.tick()
        assertEquals(listOf("1"), f.info.messages)
        f.tick(2)
        assertEquals(listOf("1"), f.info.messages)
        f.tick(4)
        assertEquals(listOf("1", "2", "3"), f.info.messages)
        assertTrue(f.info.sequences.isEmpty(), "The native ready sequence must keep looping")
        assertEquals(START, f.npc.coords)
    }

    @Test
    fun `a fresh Guns spawn has its own counter`() {
        val f = WorkoutFixture()
        f.tick(9)
        val otherInfo = RecordingInfo()
        val replacement = Npc("npc.tol_npc_builder04", START).apply { infoProtocol = otherInfo }
        repeat(3) { f.timers.process(replacement) }
        assertEquals(listOf("1"), otherInfo.messages)
        f.tick(3)
        assertEquals("4", f.info.messages.last())
    }

    @Test
    fun `Guns announces losing the count and starts the next set at one`() {
        for (restartAt in listOf(8200, 10000)) {
            val random = object : GameRandom by DefaultGameRandom(1) {
                override fun of(minInclusive: Int, maxInclusive: Int): Int {
                    assertEquals(8200, minInclusive)
                    assertEquals(10000, maxInclusive)
                    return restartAt
                }
            }
            val f = WorkoutFixture(random)
            f.tick(restartAt * 3)
            assertEquals(restartAt.toString(), f.info.messages.last())
            assertFalse(f.info.messages.contains("Start again!"))
            f.tick(6)
            assertEquals(listOf(restartAt.toString(), "Start again!", "1"), f.info.messages.takeLast(3))
        }
    }

    private class WorkoutFixture(random: GameRandom = DefaultGameRandom(1)) {
        val info = RecordingInfo()
        val npc = Npc("npc.tol_npc_builder04", START).apply { infoProtocol = info }
        private val events = EventBus()
        val timers = AiTimerProcessor(events)

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(TheGuns(random)) { context.startup() }
        }

        fun tick(count: Int = 1) = repeat(count) { timers.process(npc) }
    }

    private class RecordingInfo : NpcInfoProtocol by NoopNpcInfo {
        val messages = mutableListOf<String>()
        val sequences = mutableListOf<Int>()
        override fun setSay(text: String) { messages += text }
        override fun setSequence(seq: Int, delay: Int) { sequences += seq }
    }

    private fun movement(): NpcMovementProcessor {
        val collision = CollisionFlagMap()
        collision.allocateIfAbsent(START.x, START.z, START.level)
        return NpcMovementProcessor(collision, StepFactory(collision), EventBus())
    }

    companion object {
        private val START = CoordGrid(3200, 3200, 0)
        private val fixedTypes = buildList {
            addAll(listOf(
                "slayer_cave_entomologist", "dorgesh_baby2", "dorgesh_baby4", "dorgesh_goblin_frogspit",
                "varrock_granny_2", "dream_cyrisus_unconscious", "tol_npc_barry01", "tol_npc_builder01",
                "tol_npc_builder02", "tol_npc_builder03", "tol_npc_builder04", "death_ig_solider_sitting1",
                "death_ig_solider_sitting2", "death_ig_solider_sitting3", "death_ig_solider_wounded",
                "horror_lighthousekeeeper_injured", "sos_guard", "slug2_oniall", "shayzien_handholds_guard",
                "hosidius_tithe_farmer", "trail_falo_the_bard", "pestlemortar_crusher", "wint_pisc_navigator",
                "blast_furnace_automaton_pedaller", "blast_furnace_automaton_pumper",
                "fossil_swamp_corrupted_archaeologist", "fossil_john_camp", "fossil_david_camp",
                "corscurs_resource_meditate", "slepe_posh_woman1", "slepe_posh_man4", "prif_lenny",
                "prif_slippers", "wildy_hub_refugee1", "wildy_hub_refugee4", "clan_medieval_stool_cat",
                "wgs_washerman",
            ))
            for (variant in 1..4) {
                for (direction in listOf("north", "south", "east", "west")) {
                    add("shayzien_wounded_down_${variant}_$direction")
                }
                add("hosidius_servery_diner_north_$variant")
                add("hosidius_servery_diner_south_$variant")
            }
            for (variant in 1..3) add("molch_shay_wounded_$variant")
            for (variant in 1..6) add("dmine_miner_$variant")
        }

        @JvmStatic
        @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}
