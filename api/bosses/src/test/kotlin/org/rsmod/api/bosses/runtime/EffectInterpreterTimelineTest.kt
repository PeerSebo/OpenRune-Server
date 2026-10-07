package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import net.rsprot.protocol.game.outgoing.camera.CamReset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ForcedAbility
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.bosses.spec.TimerSpec
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class EffectInterpreterTimelineTest {
    @Test
    fun `shield and defenceless graphics use separate npc slots`() {
        val f = Fixture()
        val slots = mutableListOf<Int>()
        f.npc.infoProtocol = object : NpcInfoProtocol by NoopNpcInfo {
            override fun setSpotanim(spotanim: Int, delay: Int, height: Int, slot: Int) {
                slots += slot
            }
        }
        f.run(Effect.Spotanim("spotanim.luc2_undead_demon_shield_spot", slot = 4))
        f.run(Effect.Spotanim("spotanim.luc2_undead_accuracy_debuff", slot = 5))
        assertEquals(listOf(4, 5), slots)
    }

    @Test
    fun `unlocking an ability respects the enclosing phase movement lock`() {
        for (phaseLocked in listOf(false, true)) {
            val f = Fixture(phase = PhaseSpec("main", lockMovement = phaseLocked))
            f.run(sequence(Effect.LockMovement(true), Effect.Wait(2), Effect.LockMovement(false)))
            assertTrue(f.npc.movementLocked)
            f.advance(2)
            assertEquals(phaseLocked, f.npc.movementLocked)
        }
    }

    @Test
    fun `self healing caps at maximum health and stops when the encounter is replaced`() {
        val f = Fixture()
        f.npc.hitpoints = 90
        f.run(Effect.HealSelf(20))
        assertEquals(100, f.npc.hitpoints)
        f.npc.hitpoints = 50
        f.run(sequence(Effect.Wait(2), Effect.HealSelf(20)))
        f.replaceEncounter()
        f.advance(4)
        assertEquals(50, f.npc.hitpoints)
    }

    @Test
    fun `timers fire once per interval with trailing queues and stop with their encounter`() {
        val f = Fixture(timers = listOf(TimerSpec(2..2, mark("timer"))))
        f.startTimers()
        f.queues.add(100) {}
        f.advance(6)
        assertEquals(listOf("timer:2:1", "timer:4:1", "timer:6:1"), f.events)
        f.replaceEncounter()
        f.advance(4)
        assertEquals(3, f.events.size)
    }

    @Test
    fun `bound tiles remain captured across waits and projectile impacts`() {
        val f = Fixture()
        f.recordTiles()
        val launchTile = f.target.coords
        val effect = Effect.WithTile("launch", TargetExpr.CurrentTargetTile, sequence(
            Effect.Wait(2),
            Effect.Projectile(
                spotanim = "spotanim.vfx_amoxliatl_ice_block_projectile_01",
                target = TargetExpr.CurrentTarget,
                config = ProjectileConfig(startDelay = 0, travelTime = 20, stepMultiplier = 0),
                onImpact = sequence(
                    Effect.External("tile", params = "launch", at = TargetExpr.Bound("launch")),
                    Effect.External("tile", params = "impact", at = TargetExpr.ImpactTile),
                ),
            ),
        ))
        f.run(effect)
        f.target.coords = launchTile.translate(1, 0)
        f.advance(2)
        f.target.coords = launchTile.translate(2, 0)
        f.advance(4)
        assertEquals(listOf("launch:3:3201:3200", "impact:3:3202:3200"), f.events)
    }

    @Test
    fun `combat pursuit callback runs through cooldown without accelerating attacks`() {
        val f = Fixture(registered = true,
            abilities = mapOf("attack" to mark("attack")),
            phase = PhaseSpec("main", selector = Selector.Rotation(listOf("attack"))))
        f.registerCombat()
        repeat(8) { tick ->
            f.clock.cycle = tick
            f.combat(approach = tick % 2 == 0)
        }
        assertEquals((0..7).map { "pursue:$it" }, f.events.filter { it.startsWith("pursue:") })
        assertEquals(listOf("attack:4:1"), f.events.filter { it.startsWith("attack:") })
    }

    @Test
    fun `sequence waits add elapsed ticks even when a trailing queue exists`() {
        val f = Fixture()
        f.queues.add(100) {}
        f.run(sequence(mark("start"), Effect.Wait(2), mark("second"), Effect.Wait(1), mark("third")))

        f.advance(5)

        assertEquals(listOf("start:0:1", "second:2:1", "third:3:1"), f.events)
        assertEquals(listOf(3), f.completions)
    }

    @Test
    fun `parallel starts branches together and joins the slowest before continuing`() {
        val f = Fixture()
        f.run(sequence(
            Effect.Parallel(listOf(
                sequence(mark("short"), Effect.Wait(2), mark("short-end")),
                sequence(mark("long"), Effect.Wait(4), mark("long-end")),
            )),
            mark("joined"),
        ))

        f.advance(6)

        assertEquals(listOf("short:0:1", "long:0:1", "short-end:2:1", "long-end:4:1", "joined:4:1"), f.events)
        assertEquals(listOf(4), f.completions)
        assertTrue(f.queues.isEmpty)
    }

    @Test
    fun `repeat waits for each body and puts gaps only between iterations`() {
        val f = Fixture()
        f.queues.add(100) {}
        f.run(Effect.Repeat(3..3, sequence(mark("hit"), Effect.Wait(1)), gap = 2))

        f.advance(10)

        assertEquals(listOf("hit:0:1", "hit:3:1", "hit:6:1"), f.events)
        assertEquals(listOf(7), f.completions)
        assertEquals(listOf(3..3), f.random.ranges)
    }

    @Test
    fun `repeat rolls its count once and includes both range endpoints`() {
        for ((upper, count) in listOf(false to 2, true to 4)) {
            val f = Fixture(upper)
            f.run(Effect.Repeat(2..4, mark("hit"), gap = 1))
            f.advance(6)

            assertEquals((0 until count).map { "hit:$it:1" }, f.events)
            assertEquals(listOf(count - 1), f.completions)
            assertEquals(listOf(2..4), f.random.ranges)
        }
    }

    @Test
    fun `empty composites and zero repeats complete once without invoking the body`() {
        val f = Fixture()
        f.run(sequence(
            Effect.Sequence(emptyList()),
            Effect.Parallel(emptyList()),
            Effect.Repeat(0..0, mark("unexpected"), gap = 5),
            mark("finished"),
        ))

        f.advance(6)

        assertEquals(listOf("finished:0:1"), f.events)
        assertEquals(listOf(0), f.completions)
        assertTrue(f.queues.isEmpty)
    }

    @Test
    fun `timeline launched from a queue still waits complete ticks`() {
        val f = Fixture()
        f.queues.add(1) { f.run(sequence(mark("start"), Effect.Wait(2), mark("end"))) }
        f.queues.add(100) {}

        f.advance(5)

        assertEquals(listOf("start:1:1", "end:3:1"), f.events)
        assertEquals(listOf(3), f.completions)
    }

    @Test
    fun `nested ability completes before the containing sequence resumes`() {
        val f = Fixture(abilities = mapOf("nested" to sequence(Effect.Delay(2), mark("nested"))))
        f.run(sequence(Effect.Run("nested"), mark("after")))

        f.advance(4)

        assertEquals(listOf("nested:2:1", "after:2:1"), f.events)
        assertEquals(listOf(2), f.completions)
    }

    @Test
    fun `nested choose does not consume phase rotation positions or forced attack counts`() {
        val phaseSelector = Selector.Rotation(listOf("first", "second"))
        val f = Fixture(phase = PhaseSpec("main", selector = phaseSelector,
            forceAbilities = listOf(ForcedAbility(0, "special", attackMin = 2, attackMax = 2))))
        val choose = Effect.Choose(Selector.Rotation(listOf("left", "right")),
            mapOf("left" to mark("left"), "right" to mark("right")))

        assertEquals("first", f.encounter.selectAbility(phaseSelector, 0))
        f.run(choose)
        f.run(choose)

        assertEquals(listOf("left:0:1", "right:0:1"), f.events)
        assertEquals("second", f.encounter.selectAbility(phaseSelector, 1))
        assertEquals("special", f.encounter.selectAbility(phaseSelector, 2))
    }

    @Test
    fun `nested choose cannot consume a due periodic phase ability`() {
        val selector = Selector.Rotation(listOf("basic"))
        val f = Fixture(phase = PhaseSpec("main", selector = selector,
            forceAbilities = listOf(ForcedAbility(5, "special"))))
        f.encounter.phaseEnteredTick = 0
        f.clock.cycle = 5

        f.run(Effect.Choose(Selector.Rotation(listOf("branch")), mapOf("branch" to mark("branch"))))

        assertEquals(listOf("branch:5:1"), f.events)
        assertEquals("special", f.encounter.selectAbility(selector, 5))
    }

    @Test
    fun `boss death cancels the remaining delayed sequence`() {
        val f = Fixture()
        f.run(sequence(mark("start"), Effect.Wait(2), mark("after-death")))
        f.npc.hitpoints = 0

        f.advance(4)

        assertEquals(listOf("start:0:1"), f.events)
        assertTrue(f.completions.isEmpty())
    }

    @Test
    fun `interrupt cancels old timelines while a replacement timeline completes`() {
        val f = Fixture()
        f.run(sequence(mark("old-start"), Effect.Wait(2), mark("old-end")))
        f.run(sequence(Effect.Interrupt, mark("new-start"), Effect.Wait(1), mark("new-end")))

        f.advance(4)

        assertEquals(listOf("old-start:0:1", "new-start:0:1", "new-end:1:1"), f.events)
        assertEquals(listOf(1), f.completions)
    }

    @Test
    fun `replaced encounter cannot receive delayed effects from its previous life`() {
        val f = Fixture()
        f.run(sequence(mark("start"), Effect.Wait(2), mark("stale")))
        f.replaceEncounter()

        f.advance(4)

        assertEquals(listOf("start:0:1"), f.events)
        assertTrue(f.completions.isEmpty())
    }

    @Test
    fun `after launched from a queue keeps its full delay`() {
        val f = Fixture()
        f.queues.add(1) { f.run(Effect.After(2, mark("after"))) }
        f.queues.add(100) {}

        f.advance(4)

        assertEquals(listOf("after:3:1"), f.events)
        assertEquals(listOf(1), f.completions)
    }

    @Test
    fun `camera cleanup survives boss death encounter removal and interruption`() {
        val f = Fixture()
        val client = RecordingClient()
        f.target.client = client
        f.run(Effect.After(2, Effect.CamReset(), requireAlive = false))
        f.encounter.interrupt(0)
        f.npc.hitpoints = 0
        f.replaceEncounter()

        f.advance(3)

        assertEquals(listOf(CamReset), client.messages)
    }

    @Test
    fun `camera cleanup never reaches a different player session in the same slot`() {
        val f = Fixture()
        val original = RecordingClient()
        f.target.client = original
        f.run(Effect.After(2, Effect.CamReset(), requireAlive = false))
        val replacement = f.addPlayer(f.target.slotId, f.target.coords)
        val client = RecordingClient()
        replacement.client = client

        f.advance(3)

        assertTrue(original.messages.isEmpty())
        assertTrue(client.messages.isEmpty())
    }

    @Test
    fun `logout or leaving the boss plane cancels pending target effects`() {
        for (logout in listOf(true, false)) {
            val f = Fixture()
            f.run(sequence(Effect.Wait(2), mark("invalid-target")))
            if (logout) f.target.pendingLogout = true else f.target.coords = CoordGrid(3201, 3200, 1)

            f.advance(4)

            assertTrue(f.events.isEmpty())
            assertTrue(f.completions.isEmpty())
        }
    }

    @Test
    fun `overlapping projectile callbacks retain separate impact tiles across waits and on-each`() {
        val f = Fixture()
        val first = CoordGrid(3210, 3210)
        val second = CoordGrid(3215, 3210)
        f.addPlayer(2, first)
        f.addPlayer(3, second)
        val callback = sequence(
            Effect.Wait(2),
            Effect.OnEach(
                TargetExpr.AllInRadius(0, TargetExpr.ImpactTile),
                sequence(
                    Effect.Wait(1),
                    Effect.OnEach(TargetExpr.AllInRadius(0, TargetExpr.ImpactTile), mark("impact")),
                ),
            ),
        )
        val projectile = Effect.Projectile(
            spotanim = "spotanim.vfx_amoxliatl_ice_block_projectile_01",
            config = ProjectileConfig(startDelay = 0, travelTime = 20, stepMultiplier = 0),
            onImpact = callback,
        )
        f.target.coords = first
        f.run(projectile)
        f.target.coords = second
        f.run(projectile)
        f.target.coords = CoordGrid(3220, 3210)

        f.advance(8)

        assertEquals(listOf("impact:4:2", "impact:4:3"), f.events)
        assertTrue(f.queues.isEmpty)
    }

    private class Fixture(
        upper: Boolean = false,
        abilities: Map<String, Effect> = emptyMap(),
        phase: PhaseSpec = PhaseSpec("main"),
        registered: Boolean = false,
        timers: List<TimerSpec> = emptyList(),
    ) {
        val clock = MapClock(0)
        val queues = WorldQueueList()
        val random = EndpointRandom(upper)
        val players = PlayerList()
        val events = mutableListOf<String>()
        val completions = mutableListOf<Int>()
        val target = addPlayer(1, CoordGrid(3201, 3200))
        val npc = (if (registered) Npc("npc.hillgiant_boss", CoordGrid(3200, 3200)) else
            Npc(NpcServerType(id = 1, name = "Timeline boss", size = 1, hitpoints = 100), CoordGrid(3200, 3200))).apply {
            slotId = 1
            assignUid()
        }
        private val spec = BossSpec(if (registered) listOf("npc.hillgiant_boss") else emptyList(), BossStats(), abilities, mapOf("main" to phase), emptyList(), timers = timers)
        private val registry = EncounterRegistry(clock).apply { register(npc.type.id, listOf(spec), spec) }
        val encounter = registry.of(npc)
        private val extensions = BossExtensionRegistry().apply {
            register("record") { _, _, target, params -> events += "$params:${clock.cycle}:${target.slotId}" }
        }
        private val deps = BossDeps(random, unused<AccuracyFormulae>(), WorldRepository(ZoneUpdateMap()),
            unused<NpcRepository>(), players, clock, queues, CollisionFlagMap(), registry, extensions, PlayerHitModifier {})
        private val access = StandardNpcAccess(npc, GameCoroutine(), StandardNpcAccessContext(
            { random }, { error("Hit modifier is not used by timeline effects") }, { error("Hit processor is not used by timeline effects") },
        ))
        private val process = WorldQueueListProcess(queues)
        private val bus = EventBus()

        fun registerCombat() {
            BossCombat.register(ScriptContext(bus, CheatCommandMap(), EngineQueueCache()), spec, deps,
                onCombatTick = { _, _ -> events += "pursue:${clock.cycle}" })
        }

        fun combat(approach: Boolean) {
            npc.launch {
                if (approach) bus.publish(access, AiPlayerEvents.Ap2(target, npc))
                else bus.publish(access, AiPlayerEvents.Op2(target, npc))
            }
        }

        fun addPlayer(slot: Int, tile: CoordGrid): Player = Player().apply {
            slotId = slot
            uuid = slot.toLong()
            assignUid()
            coords = tile
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            players[slot] = this
        }

        fun run(effect: Effect) {
            EffectInterpreter(npc, target, spec, encounter, deps).run(access, effect) {
                completions += clock.cycle
            }
        }

        fun replaceEncounter() {
            registry.remove(npc)
            registry.of(npc)
        }

        fun startTimers() {
            encounter.phaseEnteredTick = clock.cycle
            encounter.lastTarget = target
            deps.startTimers(encounter, clock.cycle)
        }

        fun recordTiles() {
            extensions.register("tile") { context ->
                val tile = checkNotNull(context.tile)
                events += "${context.params}:${clock.cycle}:${tile.x}:${tile.z}"
            }
        }

        fun advance(ticks: Int) {
            repeat(ticks) {
                clock.cycle++
                npc.currentMapClock = clock.cycle
                for (player in players) player.currentMapClock = clock.cycle
                process.process()
            }
        }
    }

    private class EndpointRandom(private val upper: Boolean) : GameRandom {
        val ranges = mutableListOf<IntRange>()
        override fun of(maxExclusive: Int): Int = if (upper) maxExclusive - 1 else 0
        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            ranges += minInclusive..maxInclusive
            return if (upper) maxInclusive else minInclusive
        }
        override fun randomDouble(): Double = 0.0
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
        @JvmStatic
        @BeforeAll
        fun loadCache() { ServerCacheManager.init(241).close() }

        private fun mark(name: String): Effect = Effect.External("record", name)
        private fun sequence(vararg effects: Effect): Effect = Effect.Sequence(effects.toList())

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
