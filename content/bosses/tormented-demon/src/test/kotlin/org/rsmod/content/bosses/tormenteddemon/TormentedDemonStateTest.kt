package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class TormentedDemonStateTest {
    @Test
    fun `defenceless status starts at the deadline once and retains the client model safeguard`() {
        val f = Fixture()
        f.postAttack()
        assertEquals(130, f.npc.vars["varn.td_defenceless_at"])
        f.clock.cycle = 129
        f.postAttack()
        assertEquals(0, f.npc.vars["varn.td_defenceless"])
        assertEquals(0, f.npc.vars["varn.guaranteed_hit"])
        f.clock.cycle = 130
        f.postAttack()
        f.clock.cycle = 131
        f.postAttack()
        assertEquals(1, f.npc.vars["varn.td_defenceless"])
        assertEquals(1, f.npc.vars["varn.guaranteed_hit"])
        assertEquals(listOf(5), f.spotSlots)
        assertEquals(0, f.bodyUpdates)
    }

    @Test
    fun `respawn resets all scalar combat state and rearms the delay for the new fight`() {
        val f = Fixture()
        for (symbol in combatVars) f.npc.vars[symbol] = 123
        f.npc.vars["varn.td_shield_up"] = 0
        f.events.publish(NpcStateEvents.Respawn(f.npc))
        for (symbol in combatVars) assertEquals(0, f.npc.vars[symbol], symbol)
        assertEquals(1, f.npc.vars["varn.td_shield_up"])
        assertEquals(1, f.npc.vars["varn.td_overhead_style"])
        f.clock.cycle = 200
        f.postAttack()
        assertEquals(230, f.npc.vars["varn.td_defenceless_at"])
        assertEquals(0, f.bodyUpdates)
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val spotSlots = mutableListOf<Int>()
        var bodyUpdates = 0
        val npc = Npc("npc.tormented_demon_1", CoordGrid(3200, 3200)).apply {
            slotId = 1
            assignUid()
            infoProtocol = object : NpcInfoProtocol by NoopNpcInfo {
                override fun setBodyModel(model: Int) { bodyUpdates++ }
                override fun resetBodyModel() { bodyUpdates++ }
                override fun setSpotanim(spotanim: Int, delay: Int, height: Int, slot: Int) {
                    spotSlots += slot
                }
            }
        }
        private val target = Player().apply { coords = CoordGrid(3201, 3200) }
        private val deps = BossDeps(
            object : GameRandom {
                override fun of(maxExclusive: Int) = 0
                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
                override fun randomDouble() = 0.0
            },
            unused<AccuracyFormulae>(), unused<WorldRepository>(), unused<NpcRepository>(),
            PlayerList(), clock, WorldQueueList(), CollisionFlagMap(), EncounterRegistry(clock),
            BossExtensionRegistry(), PlayerHitModifier {},
        )
        private val script = TormentedDemon(deps, unused<RouteFactory>())

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            events.publish(NpcStateEvents.Spawn(npc))
        }

        fun postAttack() {
            val melee = script.spec.abilities.getValue("melee") as Effect.Sequence
            EffectInterpreter(npc, target, script.spec, deps.encounterRegistry.of(npc), deps)
                .run(null, melee.effects.last())
        }
    }

    companion object {
        private val combatVars = listOf(
            "varn.td_damage_since_swap", "varn.td_first_hit_taken", "varn.td_defenceless",
            "varn.td_defenceless_at", "varn.guaranteed_hit",
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
