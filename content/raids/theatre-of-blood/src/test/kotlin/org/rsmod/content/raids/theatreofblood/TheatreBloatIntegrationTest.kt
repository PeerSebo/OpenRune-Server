package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

@ResourceLock("ServerCacheManager")
class TheatreBloatIntegrationTest {
    @Test
    fun `the first sleep window is sampled per tick and always closes by forty seven ticks`() {
        for (seed in 1L..12L) {
            val fixture = Fixture(seed)
            val run = fixture.party(1)
            fixture.encounters.start(run)
            checkNotNull(run.boss).vars["varn.theatre_bloat_turn_remaining"] = 10000
            var down = 0
            for (tick in 1..47) {
                run.tick = tick
                fixture.encounters.tick(run)
                if (run.phase == 1) {
                    down = tick
                    break
                }
                fixture.movement.process(checkNotNull(run.boss))
            }
            assertTrue(down in 39..47, "Seed $seed went down at $down")
        }
    }

    @Test
    fun `the complete five tile footprint follows both square routes without cutting corners`() {
        for (direction in 0..1) {
            val fixture = Fixture()
            val run = fixture.party(1)
            fixture.encounters.start(run)
            val boss = checkNotNull(run.boss)
            uninterrupted(boss)
            boss.vars["varn.theatre_style"] = direction
            boss.vars["varn.theatre_target_pillar"] = if (direction == 0) 1 else 0
            boss.abortRoute()
            val visited = mutableSetOf<CoordGrid>()
            repeat(100) {
                run.tick++
                val previous = boss.coords
                fixture.encounters.tick(run)
                fixture.movement.process(boss)
                val source = fixture.manager.source(run, boss.coords)
                visited += source
                assertEquals(1, boss.coords.chebyshevDistance(previous), "Stalled on $source in direction $direction")
                assertTrue(source.x == 3288 || source.x == 3299 || source.z == 4440 || source.z == 4451)
                for (x in 0 until boss.size) for (z in 0 until boss.size) {
                    assertFalse(fixture.flags.isWalkBlocked(boss.coords.translate(x, z)), "Footprint clipped at $source")
                }
            }
            assertTrue(visited.containsAll(TheatreData.mechanics.bloatRoute))
        }
    }

    @Test
    fun `running moves two tiles and registered player hit impacts toggle speed below forty percent`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        uninterrupted(boss)
        boss.vars["varn.theatre_style"] = 0
        boss.vars["varn.theatre_target_pillar"] = 1
        boss.abortRoute()
        boss.hitpoints = boss.baseHitpointsLvl * 60 / 100
        run.tick = 1
        fixture.encounters.tick(run)
        assertEquals(MoveSpeed.Walk, boss.defaultMoveSpeed)
        boss.hitpoints--
        run.tick++
        fixture.encounters.tick(run)
        val previous = boss.coords
        fixture.movement.process(boss)
        assertEquals(MoveSpeed.Run, boss.defaultMoveSpeed)
        assertEquals(2, boss.coords.chebyshevDistance(previous))
        boss.hitpoints = boss.baseHitpointsLvl * 39 / 100
        val player = run.members.single()
        fixture.events.publish(NpcHitEvents.AnyImpact(boss, fixture.hit(player, damage = 0)))
        fixture.encounters.tick(run)
        assertEquals(MoveSpeed.Walk, boss.defaultMoveSpeed)
        fixture.events.publish(NpcHitEvents.AnyImpact(boss, fixture.hit(player, damage = 0)))
        fixture.encounters.tick(run)
        assertEquals(MoveSpeed.Run, boss.defaultMoveSpeed)
        fixture.events.publish(NpcHitEvents.AnyImpact(boss, fixture.hit(fixture.player(), damage = 0)))
        assertEquals(1, boss.vars["varn.theatre_bloat_run_toggle"])
    }

    @Test
    fun `a speed change keeps Bloat moving for five ticks after his sleep deadline`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.vars["varn.theatre_bloat_turn_remaining"] = 10000
        boss.vars["varn.theatre_walk_until"] = 20
        boss.vars["varn.theatre_bloat_walk_end"] = 20
        boss.hitpoints = boss.baseHitpointsLvl / 2
        for (tick in 20..24) {
            run.tick = tick
            fixture.encounters.tick(run)
            assertEquals(0, run.phase)
            assertFalse(boss.movementLocked)
        }
        run.tick = 25
        fixture.encounters.tick(run)
        assertEquals(1, run.phase)
        assertTrue(boss.movementLocked)
        assertTrue(boss.routeDestination.isEmpty())
        val hit = NpcHitEvents.BeforeImpact(boss, fixture.hit(run.members.single(), damage = 31))
        fixture.events.publish(hit)
        assertEquals(31, hit.damage)
    }

    @Test
    fun `random reversals respect the walking cooldown and keep following the collision safe ring`() {
        val fixture = Fixture(42)
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.vars["varn.theatre_walk_until"] = 10000
        boss.vars["varn.theatre_bloat_walk_end"] = 10008
        var previousTurn = 0
        var turns = 0
        repeat(300) {
            run.tick++
            val direction = boss.vars["varn.theatre_style"]
            val previous = boss.coords
            fixture.encounters.tick(run)
            if (direction != boss.vars["varn.theatre_style"]) {
                assertTrue(run.tick - previousTurn >= TheatreData.mechanics.bloatTurnTicks)
                assertEquals(run.tick, boss.vars["varn.theatre_bloat_move_change"])
                previousTurn = run.tick
                turns++
            }
            fixture.movement.process(boss)
            assertEquals(1, boss.coords.chebyshevDistance(previous))
            val source = fixture.manager.source(run, boss.coords)
            assertTrue(source.x == 3288 || source.x == 3299 || source.z == 4440 || source.z == 4451)
        }
        assertTrue(turns >= 2)
    }

    @Test
    fun `the direction cooldown pauses for the entire sleeping phase`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        sleep(fixture, run)
        boss.vars["varn.theatre_bloat_turn_remaining"] = 10
        for (tick in 51..81) {
            run.tick = tick
            fixture.encounters.tick(run)
            assertEquals(10, boss.vars["varn.theatre_bloat_turn_remaining"])
        }
        run.tick = 82
        fixture.encounters.tick(run)
        assertEquals(9, boss.vars["varn.theatre_bloat_turn_remaining"])
    }

    @Test
    fun `stomp checks pillar visibility once at tick thirty and movement resumes at tick thirty two`() {
        val fixture = Fixture()
        val run = fixture.party(1, size = 2)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        run.members[0].coords = fixture.manager.coord(run, CoordGrid(3303, 4441, 0))
        run.members[1].coords = fixture.manager.coord(run, CoordGrid(3295, 4452, 0))
        sleep(fixture, run)
        boss.defenceLvl = 5
        fixture.hits.clear()
        run.tick = 79
        fixture.encounters.tick(run)
        assertTrue(fixture.hits.isEmpty())
        run.tick = 80
        fixture.encounters.tick(run)
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
        assertTrue(fixture.hits.single().damage in 40..80)
        assertEquals(boss.type.defence, boss.defenceLvl)
        assertEquals(1, run.phase)
        run.tick = 81
        fixture.encounters.tick(run)
        assertEquals(1, fixture.hits.size)
        fixture.events.publish(NpcHitEvents.AnyImpact(boss, fixture.hit(run.members[0], damage = 0)))
        run.tick = 82
        fixture.encounters.tick(run)
        assertEquals(0, run.phase)
        assertFalse(boss.movementLocked)
        assertEquals(82 + 34, boss.vars["varn.theatre_walk_until"])
        assertEquals(82 + 42, boss.vars["varn.theatre_bloat_walk_end"])
    }

    @Test
    fun `an untouched down retains the longer next walking window`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        sleep(fixture, run)
        run.tick = 82
        fixture.encounters.tick(run)
        assertEquals(82 + 39, boss.vars["varn.theatre_walk_until"])
        assertEquals(82 + 47, boss.vars["varn.theatre_bloat_walk_end"])
    }

    @Test
    fun `flies spread across visible players without a distance cap or a second generation`() {
        val fixture = Fixture()
        val run = fixture.party(1, size = 3)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        uninterrupted(boss)
        val positions = listOf(CoordGrid(3303, 4441, 0), CoordGrid(3303, 4452, 0), CoordGrid(3295, 4452, 0))
        run.members.zip(positions).forEach { (player, point) -> player.coords = fixture.manager.coord(run, point) }
        VarPlayerIntMapSetter.set(run.members[1], "varbit.prayer_protectfrommissiles", 1)
        val rays = RayCastValidator(fixture.flags)
        val bloat = TheatreBloat(fixture.manager, rays)
        assertTrue(bloat.visible(boss, run.members[0]))
        assertFalse(bloat.visible(boss, run.members[1]))
        assertFalse(bloat.visible(boss, run.members[2]))
        assertTrue(rays.hasLineOfSight(run.members[0].coords, run.members[1].coords))
        assertFalse(rays.hasLineOfSight(run.members[0].coords, run.members[2].coords))
        assertTrue(rays.hasLineOfSight(run.members[1].coords, run.members[2].coords))
        run.tick = 1
        fixture.updates.clear()
        fixture.encounters.tick(run)
        val projectiles = fixture.updates.updatedZones.values.flatMap { it.toList() }
            .filterIsInstance<MapProjAnimV2>()
        assertEquals(2, projectiles.size)
        assertEquals(listOf("spotanim.tob_bloat_flies_large", "spotanim.tob_bloat_flies_small")
            .map { it.asRSCM(RSCMType.SPOTANIM) }, projectiles.map { it.id })
        fixture.advancePlayerHits()
        assertEquals(2, fixture.hits.size)
        assertTrue(fixture.hits[0].damage in 10..20)
        assertTrue(fixture.hits[1].damage in 7..15)
    }

    @Test
    fun `the closest side can see around a pillar corner even when the southwest tile cannot`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val player = run.members.single()
        val rays = RayCastValidator(fixture.flags)
        val bloat = TheatreBloat(fixture.manager, rays)
        val edgeVisible = buildList {
            val bounds = run.roomrow.bounds
            for (x in bounds[0]..bounds[2]) for (z in bounds[1]..bounds[3]) {
                player.coords = fixture.manager.coord(run, CoordGrid(x, z, 0))
                if (!fixture.flags.isWalkBlocked(player.coords) &&
                    !rays.hasLineOfSight(boss.coords, player.coords) && bloat.visible(boss, player)) {
                    add(player.coords)
                }
            }
        }
        assertTrue(edgeVisible.isNotEmpty(), "Native geometry must allow visibility from an edge tile")
    }

    @Test
    fun `each visible player receives one fly hit rather than duplicate direct and spread hits`() {
        val fixture = Fixture()
        val run = fixture.party(1, size = 2)
        fixture.encounters.start(run)
        uninterrupted(checkNotNull(run.boss))
        run.members[0].coords = fixture.manager.coord(run, CoordGrid(3303, 4441, 0))
        run.members[1].coords = fixture.manager.coord(run, CoordGrid(3302, 4441, 0))
        run.tick = 1
        fixture.encounters.tick(run)
        fixture.advancePlayerHits()
        assertEquals(2, fixture.hits.size)
    }

    @Test
    fun `flesh drops are distinct walkable batches and never reuse the previous wave`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        uninterrupted(boss)
        boss.hitpoints = boss.baseHitpointsLvl * 80 / 100
        run.tick = 6
        fixture.updates.clear()
        fixture.encounters.tick(run)
        val first = flesh(fixture)
        assertEquals(16, first.size)
        assertEquals(16, first.toSet().size)
        assertTrue(first.all { !fixture.flags.isWalkBlocked(it) })
        assertEquals(12, boss.vars["varn.theatre_bloat_flesh_next"])
        run.tick = 12
        fixture.updates.clear()
        fixture.encounters.tick(run)
        val second = flesh(fixture)
        assertEquals(16, second.size)
        assertTrue(first.toSet().intersect(second.toSet()).isEmpty())
        boss.hitpoints = boss.baseHitpointsLvl / 2
        run.tick = 18
        fixture.encounters.tick(run)
        assertEquals(22, boss.vars["varn.theatre_bloat_flesh_next"])
    }

    @Test
    fun `falling flesh tests occupancy at impact and removes its stun when the timer expires`() {
        val fixture = Fixture()
        val run = fixture.party(1)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        uninterrupted(boss)
        boss.hitpoints = boss.baseHitpointsLvl * 80 / 100
        run.tick = 6
        fixture.updates.clear()
        fixture.encounters.tick(run)
        val player = run.members.single()
        player.coords = flesh(fixture).first()
        fixture.advancePlayerHits()
        fixture.hits.clear()
        fixture.updates.clear()
        repeat(TheatreData.mechanics.bloatFleshDelayTicks - 1) { fixture.advanceQueues() }
        assertTrue(fixture.hits.isEmpty())
        assertTrue(fixture.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<SoundArea>().isEmpty())
        fixture.advanceQueues()
        assertTrue(fixture.hits.isEmpty())
        val sounds = fixture.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<SoundArea>()
        assertEquals(TheatreData.mechanics.bloatFleshCount, sounds.size)
        assertTrue(sounds.all { it.id == "synth.BARBASSAULT_SPLAT".asRSCM(RSCMType.SYNTH) && it.delay == 0 })
        assertTrue(sounds.none { it.id == "synth.TOB_BLOAT_HIT".asRSCM(RSCMType.SYNTH) })
        assertTrue(Stun.isStunned(player))
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
        assertTrue(fixture.hits.single().damage in 30..50)
        player.currentMapClock += TheatreData.mechanics.bloatFleshStunTicks
        assertFalse(Stun.isStunned(player))
    }

    @Test
    fun `a defeated boss or a cleared room cannot deliver delayed flesh hits`() {
        for (ended in listOf(false, true)) {
            val fixture = Fixture()
            val run = fixture.party(1)
            fixture.encounters.start(run)
            val boss = checkNotNull(run.boss)
            uninterrupted(boss)
            boss.hitpoints = boss.baseHitpointsLvl * 80 / 100
            run.tick = 6
            fixture.updates.clear()
            fixture.encounters.tick(run)
            run.members.single().coords = flesh(fixture).first()
            fixture.hits.clear()
            if (ended) fixture.manager.roomCleared(run) else boss.hitpoints = 0
            repeat(TheatreData.mechanics.bloatFleshDelayTicks) { fixture.advanceQueues() }
            assertTrue(fixture.hits.isEmpty())
        }
    }

    private fun flesh(fixture: Fixture): List<CoordGrid> {
        val ids = TheatreData.mechanics.bloatFleshGraphics.map { it.asRSCM(RSCMType.SPOTANIM) }
        return fixture.updates.updatedZones.int2ObjectEntrySet().flatMap { entry ->
            entry.value.toList().filterIsInstance<MapAnim>().filter { it.id in ids }
                .map { ZoneKey(entry.intKey).toCoords().translate(it.xInZone, it.zInZone) }
        }
    }

    private fun uninterrupted(boss: org.rsmod.game.entity.Npc) {
        boss.vars["varn.theatre_walk_until"] = 10000
        boss.vars["varn.theatre_bloat_walk_end"] = 10008
        boss.vars["varn.theatre_bloat_turn_remaining"] = 10000
    }

    private fun sleep(fixture: Fixture, run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        boss.vars["varn.theatre_walk_until"] = 50
        boss.vars["varn.theatre_bloat_walk_end"] = 50
        boss.vars["varn.theatre_bloat_turn_remaining"] = 10000
        run.tick = 50
        fixture.encounters.tick(run)
        assertEquals(1, run.phase)
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}

private typealias Fixture = TheatreEncounterIntegrationTest.Fixture
