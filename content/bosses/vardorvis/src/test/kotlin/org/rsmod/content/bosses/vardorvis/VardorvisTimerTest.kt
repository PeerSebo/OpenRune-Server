package org.rsmod.content.bosses.vardorvis

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class VardorvisTimerTest {
    @Test
    fun `axe timer stays unarmed until the first hit starts the encounter`() {
        val f = Fixture()
        repeat(20) { f.tick() }
        assertTrue(f.actions.isEmpty())
        assertEquals(0, f.npc.vars["varn.vardorvis_next_axes"])
        assertEquals(0, f.npc.vars["varn.vardorvis_strangle_ready_at"])
    }

    @Test
    fun `opening set is axes and a due timer never fires twice in one tick`() {
        val f = Fixture()
        f.npc.vars["varn.vardorvis_next_axes"] = 115
        f.npc.vars["varn.vardorvis_strangle_ready_at"] = 116
        f.clock.cycle = 114
        f.runTimer()
        assertTrue(f.actions.isEmpty())
        f.clock.cycle = 115
        f.runTimer()
        f.runTimer()
        assertEquals(listOf("axes:115"), f.actions)
        assertEquals(128, f.npc.vars["varn.vardorvis_next_axes"])
        f.clock.cycle = 128
        f.runTimer()
        f.runTimer()
        assertEquals(2, f.actions.size)
        assertTrue(f.actions.last() in setOf("axes:128", "strangle:128"))
        if (f.actions.last() == "strangle:128") {
            assertEquals(153, f.npc.vars["varn.vardorvis_strangle_ready_at"])
        } else {
            assertEquals(116, f.npc.vars["varn.vardorvis_strangle_ready_at"])
        }
        assertTrue(f.npc.vars["varn.vardorvis_next_axes"] > f.clock.cycle)
    }

    private class Fixture {
        val clock = MapClock(100)
        val actions = mutableListOf<String>()
        val npc = Npc("npc.vardorvis", CoordGrid(3200, 3200))
        private val target = Player().apply { coords = CoordGrid(3201, 3200) }
        private val registry = EncounterRegistry(clock)
        private val extensions = BossExtensionRegistry().apply {
            register("vardorvis.axes") { _, _, _, _ -> actions += "axes:${clock.cycle}" }
            register("vardorvis.strangle") { _, _, _, _ -> actions += "strangle:${clock.cycle}" }
        }
        private val deps = BossDeps(
            object : GameRandom {
                override fun of(maxExclusive: Int) = 0
                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
                override fun randomDouble() = 0.0
            },
            unused<AccuracyFormulae>(), unused<WorldRepository>(), unused<NpcRepository>(),
            PlayerList(), clock, WorldQueueList(), CollisionFlagMap(), registry, extensions,
            PlayerHitModifier {},
        )
        private val spec = Vardorvis(
            deps, NpcList(), unused<LocRepository>(), unused<AiPlayerInteractions>(),
            unused<VardorvisStrangle>(), unused<MusicPlayer>(),
        ).spec

        init { registry.register(npc.id, listOf(spec), spec) }

        fun runTimer() {
            EffectInterpreter(npc, target, spec, registry.of(npc), deps)
                .run(null, spec.phases.getValue("main").timers.single().effect)
        }

        fun tick() { runTimer(); clock.cycle++ }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
