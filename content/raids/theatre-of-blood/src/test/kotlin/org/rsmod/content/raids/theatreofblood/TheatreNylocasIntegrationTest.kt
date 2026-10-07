package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import kotlinx.coroutines.runBlocking
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.content.raids.theatreofblood.TheatreEncounterIntegrationTest.Fixture
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class TheatreNylocasIntegrationTest {
    @Test
    fun `first wave waits for the second cycle and subsequent waves stay on that cycle`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        assertEquals(4, pillars(run).size)
        for (tick in 1 until TheatreData.rules.nylocasFirstWaveTicks) {
            run.tick = tick
            fixture.nylocas.tick(run)
            assertTrue(spiders(run).isEmpty())
        }
        run.tick = TheatreData.rules.nylocasFirstWaveTicks
        fixture.nylocas.tick(run)
        assertEquals(1, run.controller.vars[WAVE])
        assertEquals(3, spiders(run).size)
        repeat(3) {
            run.tick++
            fixture.nylocas.tick(run)
            assertEquals(1, run.controller.vars[WAVE])
        }
        run.tick++
        fixture.nylocas.tick(run)
        assertEquals(2, run.controller.vars[WAVE])
    }

    @Test
    fun `dying spiders still consume the cap and killing one cannot resume waves off cycle`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        repeat(12) { spider(fixture, run) }
        val dying = spiders(run).last()
        dying.hitpoints = 0
        dying.vars[DYING] = 1
        run.tick = TheatreData.rules.nylocasFirstWaveTicks
        fixture.nylocas.tick(run)
        assertEquals(0, run.controller.vars[WAVE])
        fixture.manager.remove(run, dying)
        repeat(3) {
            run.tick++
            fixture.nylocas.tick(run)
            assertEquals(0, run.controller.vars[WAVE])
        }
        run.tick++
        fixture.nylocas.tick(run)
        assertEquals(1, run.controller.vars[WAVE])
    }

    @Test
    fun `wave twenty raises the cap to twenty four`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        repeat(12) { spider(fixture, run) }
        run.controller.vars[WAVE] = 19
        run.tick = TheatreData.rules.nylocasFirstWaveTicks
        fixture.nylocas.tick(run)
        assertEquals(20, run.controller.vars[WAVE])
        while (spiders(run).size < 24) spider(fixture, run)
        run.tick += TheatreData.rules.nylocasWaveTicks
        fixture.nylocas.tick(run)
        assertEquals(20, run.controller.vars[WAVE])
    }

    @Test
    fun `small and large wave health uses the explicit scale rows`() {
        val small = listOf(8, 8, 8, 9, 11)
        val large = listOf(16, 16, 16, 19, 22)
        for (size in 1..5) {
            val fixture = Fixture()
            val run = fixture.party(2, size)
            fixture.nylocas.start(run)
            run.controller.vars[WAVE] = 8
            run.tick = TheatreData.rules.nylocasFirstWaveTicks
            fixture.nylocas.tick(run)
            assertTrue(spiders(run).any { it.size == 2 })
            for (npc in spiders(run)) {
                assertEquals(if (npc.size == 2) large[size - 1] else small[size - 1], npc.hitpoints)
                assertEquals(MoveRestrict.BlockedNormal, npc.moveRestrict)
            }
        }
    }

    @Test
    fun `registered large death waits five ticks and splits exactly once at despawn`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        val parent = spider(fixture, run, big = true)
        val sequences = sequences(parent)
        parent.hitpoints = 0
        val access = StandardNpcAccess(parent, GameCoroutine("nylo-death"), StandardNpcAccessContext(
            getRandom = { error("unused") }, getHitModifier = { error("unused") },
            getHitProcessor = { error("unused") },
        ))
        runBlocking {
            assertTrue(fixture.events.publish(access, NpcQueueEvents.Type(parent, null,
                "queue.death".asRSCM(RSCMType.QUEUE))))
        }
        fixture.nylocas.dying(run, parent)
        assertEquals(listOf("seq.top_spider_melee_death".asRSCM(RSCMType.SEQ)), sequences)
        assertTrue(parent.movementLocked)
        repeat(4) {
            fixture.advanceQueues()
            assertTrue(parent.isSlotAssigned)
            assertEquals(listOf(parent), spiders(run))
        }
        fixture.advanceQueues()
        assertFalse(parent.isSlotAssigned)
        assertNull(fixture.manager.run(parent))
        assertEquals(2, spiders(run).size)
        assertTrue(spiders(run).all { it.size == 1 && it.vars[BORN] == run.tick })
        fixture.nylocas.dying(run, parent)
        fixture.advanceQueues()
        assertEquals(2, spiders(run).size)
    }

    @Test
    fun `small death occupies one tick then releases the slot`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        val npc = spider(fixture, run)
        fixture.nylocas.dying(run, npc)
        assertTrue(npc.isSlotAssigned)
        assertEquals(0, fixture.nylocas.beforeImpact(run, npc,
            fixture.hit(run.members.single(), damage = 10), 10))
        fixture.advanceQueues()
        assertFalse(npc.isSlotAssigned)
        assertTrue(spiders(run).isEmpty())
    }

    @Test
    fun `natural large explosion waits three ticks and still produces children`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        val npc = spider(fixture, run, big = true)
        val player = run.members.single()
        player.coords = npc.coords.translate(-1, 0)
        run.tick = TheatreData.rules.nylocasLifetimeTicks - 1
        fixture.nylocas.tick(run)
        assertEquals(0, npc.vars[DYING])
        run.tick++
        fixture.nylocas.tick(run)
        assertEquals(2, npc.vars[DYING])
        fixture.hits.clear()
        repeat(2) { fixture.advanceQueues(); assertTrue(npc.isSlotAssigned) }
        assertTrue(fixture.hits.isEmpty())
        fixture.advanceQueues()
        assertFalse(npc.isSlotAssigned)
        assertEquals(2, spiders(run).size)
        assertTrue(fixture.hits.isEmpty())
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
        assertTrue(fixture.hits.single().damage in 0..TheatreData.rules.nylocasBigBlastMax)
    }

    @Test
    fun `queued splits and explosions are discarded when the room ends`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        val npc = spider(fixture, run, big = true)
        fixture.nylocas.dying(run, npc, natural = true)
        fixture.manager.roomCleared(run)
        repeat(6) { fixture.advanceQueues() }
        assertTrue(run.adds.isEmpty())
        assertTrue(fixture.hits.isEmpty())
    }

    @Test
    fun `every fixed wave enters through the native lanes before using arena collision`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        var total = 0
        for (wave in 1..31) {
            spiders(run).forEach { fixture.manager.remove(run, it) }
            run.controller.vars[WAVE] = wave - 1
            run.controller.vars[NEXT_WAVE] = 0
            run.tick = TheatreData.rules.nylocasFirstWaveTicks
            fixture.nylocas.tick(run)
            run.controller.vars[NEXT_WAVE] = Int.MAX_VALUE
            val spawned = spiders(run)
            assertEquals(TheatreData.nylocasWaves.getValue(wave).size, spawned.size)
            assertTrue(spawned.all { it.vars[STAGING] != 0 && it.moveRestrict == MoveRestrict.BlockedNormal })
            repeat(7) {
                run.tick++
                fixture.nylocas.tick(run)
                spawned.forEach { fixture.movement.process(it) }
            }
            for (npc in spawned) {
                assertEquals(0, npc.vars[STAGING], "Wave $wave, size ${npc.size}, at ${fixture.manager.source(run, npc.coords)}")
                assertEquals(MoveRestrict.PassThru, npc.moveRestrict)
                assertEquals(npc.vars[AGGRESSIVE] != 0, npc.visType.internalName.contains("fighting"))
                for (x in 0 until npc.size) for (z in 0 until npc.size) {
                    val point = npc.coords.translate(x, z)
                    assertFalse(fixture.flags.isWalkBlocked(point), "Wave $wave, blocked footprint at ${fixture.manager.source(run, point)}")
                }
            }
            total += spawned.size
        }
        assertEquals(120, total)
    }

    @Test
    fun `aggro spiders first enter the room and then reveal their fighting variant`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        run.controller.vars[WAVE] = 9
        run.tick = TheatreData.rules.nylocasFirstWaveTicks
        fixture.nylocas.tick(run)
        run.controller.vars[NEXT_WAVE] = Int.MAX_VALUE
        val npc = spiders(run).first { it.vars[AGGRESSIVE] == 1 }
        val staging = CoordGrid(npc.vars[STAGING])
        assertTrue(npc.visType.internalName.contains("incoming"))
        repeat(5) {
            run.tick++
            fixture.nylocas.tick(run)
            fixture.movement.process(npc)
        }
        assertEquals(0, npc.vars[STAGING], "At ${fixture.manager.source(run, npc.coords)} towards ${fixture.manager.source(run, staging)}, route=${npc.routeDestination}")
        assertTrue(npc.visType.internalName.contains("fighting"))
        assertTrue(staging.chebyshevDistance(npc.coords) <= 2)
    }

    @Test
    fun `all three pillar attacks use contact animations and cannot hit diagonally`() {
        for (style in TheatreAttackStyle.entries) {
            val fixture = Fixture()
            val run = fixture.party(2)
            fixture.nylocas.start(run)
            val pillar = pillars(run).first()
            val npc = spider(fixture, run, style = style)
            npc.vars[TARGET_PILLAR] = pillar.vars[PILLAR]
            npc.coords = pillar.coords.translate(-1, -1)
            val health = pillar.hitpoints
            fixture.nylocas.tick(run)
            assertEquals(health, pillar.hitpoints)
            npc.abortRoute()
            npc.coords = pillar.coords.translate(-1, 0)
            val played = sequences(npc)
            fixture.nylocas.tick(run)
            assertEquals(health - 1, pillar.hitpoints)
            val symbol = if (style == TheatreAttackStyle.MELEE) "seq.top_spider_melee_attack"
                else "seq.top_spider_${style.name.lowercase()}_meleeattack"
            assertEquals(listOf(symbol.asRSCM(RSCMType.SEQ)), played)
            assertTrue(npc.routeDestination.isEmpty())
        }
    }

    @Test
    fun `pillar bound spiders reach cardinal contact while actors cannot block their path`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        val npc = spider(fixture, run)
        val pillar = pillars(run).first()
        npc.vars[TARGET_PILLAR] = pillar.vars[PILLAR]
        run.members.single().coords = npc.coords.translate(-1, 0)
        val initial = pillar.hitpoints
        repeat(25) {
            run.tick++
            fixture.nylocas.tick(run)
            fixture.movement.process(npc)
            assertFalse(fixture.flags.isWalkBlocked(npc.coords))
        }
        assertTrue(pillar.hitpoints < initial, "At ${fixture.manager.source(run, npc.coords)} towards ${fixture.manager.source(run, pillar.coords)}, route=${npc.routeDestination}")
        assertEquals(1, npc.distanceTo(pillar))
    }

    @Test
    fun `wrong style locks only that member and a queued correct hit survives a style flicker`() {
        val fixture = Fixture()
        val run = fixture.party(2, 2)
        fixture.nylocas.start(run)
        val npc = spider(fixture, run)
        val wrong = fixture.builder(run.members[0], HitType.Magic, 12)
        fixture.events.publish(NpcHitEvents.AnyModify(npc, wrong))
        assertEquals(0, wrong.damage)
        assertEquals(1, npc.vars[LOCKED])
        val blocked = fixture.builder(run.members[0], HitType.Melee, 12)
        fixture.nylocas.atLaunch(run, npc, blocked)
        assertEquals(0, blocked.damage)
        val correct = fixture.builder(run.members[1], HitType.Melee, 12)
        fixture.nylocas.atLaunch(run, npc, correct)
        assertEquals(12, correct.damage)
        npc.vars[STYLE] = TheatreAttackStyle.MAGIC.ordinal
        assertEquals(12, fixture.nylocas.beforeImpact(run, npc, correct.build(), correct.damage))
        val outsider = fixture.player()
        val external = fixture.builder(outsider, HitType.Magic, 20)
        fixture.nylocas.atLaunch(run, npc, external)
        assertEquals(0, external.damage)
    }

    @Test
    fun `boss spawn waits for full despawn and five cycles then completes native introduction`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        run.controller.vars[WAVE] = 31
        val last = spider(fixture, run)
        fixture.nylocas.dying(run, last)
        run.tick = 6
        fixture.nylocas.tick(run)
        assertNull(run.boss)
        assertEquals(0, run.controller.vars[BOSS_SPAWN])
        fixture.advanceQueues()
        fixture.nylocas.tick(run)
        assertEquals(24, run.controller.vars[BOSS_SPAWN])
        for (tick in 7..23) { run.tick = tick; fixture.nylocas.tick(run); assertNull(run.boss) }
        run.tick = 24
        fixture.nylocas.tick(run)
        val boss = checkNotNull(run.boss)
        assertEquals(4, pillars(run).size)
        assertEquals(0, fixture.nylocas.beforeImpact(run, boss, fixture.hit(run.members.single(), damage = 40), 40))
        fixture.advanceQueues()
        assertEquals(1, run.controller.vars[BOSS_ACTIVE])
        fixture.advanceQueues()
        assertEquals(2, run.controller.vars[BOSS_ACTIVE])
        assertEquals("npc.nylocas_boss_melee".asRSCM(RSCMType.NPC), boss.visType.id)
        assertEquals(MoveRestrict.PassThru, boss.moveRestrict)
    }

    @Test
    fun `boss changes to a different style every ten ticks and resets attack delay`() {
        val (fixture, run) = boss()
        val npc = checkNotNull(run.boss)
        run.members.single().coords = npc.coords.translate(-1, 0)
        var previous = npc.vars[STYLE]
        repeat(5) {
            run.tick = npc.vars[NEXT_STYLE]
            fixture.nylocas.tick(run)
            assertNotEquals(previous, npc.vars[STYLE])
            assertEquals(run.tick + 10, npc.vars[NEXT_STYLE])
            assertEquals(run.tick + TheatreData.rules.nylocasTransformAttackTicks, npc.vars[ATTACK])
            assertTrue(fixture.hits.isEmpty())
            previous = npc.vars[STYLE]
        }
    }

    @Test
    fun `transform interrupts boss attacks and chasing while preserving manual walking`() {
        val (fixture, run) = boss(2)
        val npc = checkNotNull(run.boss)
        val attacker = run.members[0]
        attacker.interaction = InteractionNpcOp(InteractionOp.Op2, npc, true, false)
        attacker.routeDestination.add(npc.coords)
        val walker = run.members[1]
        walker.coords = attacker.coords
        val destination = walker.coords.translate(-1, 0)
        walker.routeDestination.add(destination)
        run.tick = npc.vars[NEXT_STYLE]
        fixture.nylocas.tick(run)
        assertNull(attacker.interaction)
        assertTrue(attacker.routeDestination.isEmpty())
        assertEquals(destination, walker.routeDestination.peekLast())
    }

    @Test
    fun `native wave display tracks pillar health and switches to boss health`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        val player = run.members.single()
        fixture.nylocas.start(run)
        fixture.presentation.update(run, listOf(player))
        assertEquals(2, player.vars["varbit.tob_client_waveprogress_type"])
        assertEquals(520, player.vars["varbit.tob_client_waveprogress_max"])
        assertEquals(520, player.vars["varbit.tob_client_waveprogress_val"])
        val remaining = pillars(run)
        remaining[0].hitpoints = 10
        fixture.manager.remove(run, remaining[1])
        fixture.presentation.update(run, listOf(player))
        assertEquals(270, player.vars["varbit.tob_client_waveprogress_val"])
        assertEquals(520, player.vars["varbit.tob_client_waveprogress_max"])
        run.boss = fixture.manager.spawn(run, "npc.nylocas_boss_melee", run.roomrow.center, 1875)
        fixture.presentation.update(run, listOf(player))
        assertEquals(1, player.vars["varbit.tob_client_waveprogress_type"])
        assertEquals(1023, player.vars["varbit.tob_client_waveprogress_val"])
        assertEquals(1023, player.vars["varbit.tob_client_waveprogress_max"])
    }

    @Test
    fun `wrong style boss hit heals visibly and reflects without contributing damage`() {
        val (fixture, run) = boss()
        val npc = checkNotNull(run.boss)
        npc.hitpoints -= 100
        val before = npc.hitpoints
        val marks = mutableListOf<Hitmark>()
        npc.infoProtocol = object : NpcInfoProtocol by npc.infoProtocol {
            override fun showHitmark(hitmark: Hitmark) { marks += hitmark }
        }
        val damage = fixture.nylocas.beforeImpact(run, npc,
            fixture.hit(run.members.single(), HitType.Magic, 30), 30)
        assertEquals(0, damage)
        assertEquals(before + 30, npc.hitpoints)
        fixture.advancePlayerHits()
        assertEquals(30, fixture.hits.single().damage)
        assertEquals("hitmark.theatre_heal".asRSCM(RSCMType.HITMARK), marks.single().public)
        assertEquals(30, fixture.nylocas.beforeImpact(run, npc,
            fixture.hit(run.members.single(), HitType.Melee, 30), 30))
    }

    @Test
    fun `ranged boss sends its native projectile and delays damage until flight finishes`() {
        val (fixture, run) = boss()
        val npc = checkNotNull(run.boss)
        npc.vars[STYLE] = TheatreAttackStyle.RANGED.ordinal
        npc.vars[NEXT_STYLE] = Int.MAX_VALUE
        run.members.single().coords = npc.coords.translate(-2, 0)
        run.tick = npc.vars[ATTACK]
        fixture.updates.clear()
        fixture.nylocas.tick(run)
        val projectiles = fixture.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<MapProjAnimV2>()
        assertEquals(1, projectiles.size)
        assertEquals("spotanim.tob_nylocas_rangedprojectile_size2".asRSCM(RSCMType.SPOTANIM), projectiles.single().id)
        assertTrue(fixture.hits.isEmpty())
        fixture.advanceQueues()
        assertTrue(fixture.hits.isEmpty())
        fixture.advanceQueues()
        assertTrue(fixture.hits.isEmpty())
        fixture.advancePlayerHits()
        assertEquals(25, fixture.hits.single().prayerPenetration)
        assertEquals(HitType.Ranged, fixture.hits.single().type)
        assertEquals(run.tick + 4, npc.vars[ATTACK])
    }

    @Test
    fun `pending boss projectiles cannot hit after death or room clear`() {
        for (clear in listOf(false, true)) {
            val (fixture, run) = boss()
            val npc = checkNotNull(run.boss)
            npc.vars[STYLE] = TheatreAttackStyle.RANGED.ordinal
            npc.vars[NEXT_STYLE] = Int.MAX_VALUE
            run.members.single().coords = npc.coords.translate(-2, 0)
            run.tick = npc.vars[ATTACK]
            fixture.nylocas.tick(run)
            if (clear) fixture.manager.roomCleared(run) else npc.hitpoints = 0
            repeat(3) { fixture.advanceQueues() }
            assertTrue(fixture.hits.isEmpty())
        }
    }

    private fun spider(fixture: Fixture, run: TheatreRun, big: Boolean = false,
        style: TheatreAttackStyle = TheatreAttackStyle.MELEE): Npc {
        val symbol = "npc.tob_nylocas_${if (big) "big_" else ""}incoming_${style.name.lowercase()}"
        return fixture.manager.spawn(run, symbol, CoordGrid(3295, 4247, 0)).apply {
            vars[STYLE] = style.ordinal
            vars[BIG] = if (big) 1 else 0
            vars[BORN] = run.tick
            moveRestrict = MoveRestrict.PassThru
        }
    }

    private fun boss(size: Int = 1): Pair<Fixture, TheatreRun> {
        val fixture = Fixture()
        val run = fixture.party(2, size)
        fixture.nylocas.start(run)
        run.controller.vars[WAVE] = 31
        run.tick = 6
        fixture.nylocas.tick(run)
        run.tick = run.controller.vars[BOSS_SPAWN]
        fixture.nylocas.tick(run)
        repeat(TheatreData.rules.nylocasBossIntroTicks) { fixture.advanceQueues() }
        return fixture to run
    }

    private fun sequences(npc: Npc): MutableList<Int> = mutableListOf<Int>().also { values ->
        npc.infoProtocol = object : NpcInfoProtocol by npc.infoProtocol {
            override fun setSequence(seq: Int, delay: Int) { values += seq }
        }
    }

    private fun pillars(run: TheatreRun) = run.adds.filter { it.vars[PILLAR] != 0 }
    private fun spiders(run: TheatreRun) = run.adds.filter { it.vars[PILLAR] == 0 && it !== run.boss }

    companion object {
        private const val WAVE = "varn.theatre_nylo_wave"
        private const val NEXT_WAVE = "varn.theatre_nylo_next_wave_tick"
        private const val STYLE = "varn.theatre_nylo_style"
        private const val NEXT_STYLE = "varn.theatre_nylo_next_style_tick"
        private const val BORN = "varn.theatre_nylo_born_tick"
        private const val BIG = "varn.theatre_nylo_big"
        private const val DYING = "varn.theatre_nylo_dying"
        private const val AGGRESSIVE = "varn.theatre_nylo_aggressive"
        private const val STAGING = "varn.theatre_nylo_staging"
        private const val PILLAR = "varn.theatre_nylo_pillar_index"
        private const val TARGET_PILLAR = "varn.theatre_target_pillar"
        private const val LOCKED = "varn.theatre_nylo_locked_members"
        private const val BOSS_ACTIVE = "varn.theatre_nylo_boss_active"
        private const val BOSS_SPAWN = "varn.theatre_nylo_boss_spawn_tick"
        private const val ATTACK = "varn.theatre_nylo_boss_attack_tick"

        @JvmStatic @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}
