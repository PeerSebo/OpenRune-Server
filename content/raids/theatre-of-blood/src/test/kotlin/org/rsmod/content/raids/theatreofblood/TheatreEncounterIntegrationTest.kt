package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.loc.MapLocListDefinition
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Provider
import java.nio.file.Paths
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.commons.npc.canRetaliate
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.accuracy.magic.NvPMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.melee.NvPMeleeAccuracy
import org.rsmod.api.combat.formulas.accuracy.ranged.NvPRangedAccuracy
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.StepFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class TheatreEncounterIntegrationTest {
    @Test
    fun `Maiden death uses both native models and completes both sequences before room clear`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val sequences = mutableListOf<Int>()
        val models = mutableListOf<Int>()
        boss.infoProtocol = object : NpcInfoProtocol by boss.infoProtocol {
            override fun setSequence(seq: Int, delay: Int) { sequences += seq }
            override fun setTransmog(type: Int) { models += type }
        }
        boss.hitpoints = 0
        val coroutine = GameCoroutine("maiden-death")
        val access = StandardNpcAccess(boss, coroutine, StandardNpcAccessContext(
            getRandom = { error("unused") }, getHitModifier = { error("unused") },
            getHitProcessor = { error("unused") },
        ))
        var complete = false
        val block: suspend () -> Unit = {
            assertTrue(fixture.events.publish(access, NpcQueueEvents.Type(boss, null, "queue.death".asRSCM(RSCMType.QUEUE))))
        }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); complete = true }
        })
        val first = "seq.maiden_death_a".asRSCM(RSCMType.SEQ)
        val second = "seq.maiden_death_b".asRSCM(RSCMType.SEQ)
        val firstTicks = checkNotNull(ServerCacheManager.getAnim(first)).tickDuration
        val secondTicks = checkNotNull(ServerCacheManager.getAnim(second)).tickDuration
        assertEquals(listOf(first), sequences)
        assertEquals(listOf("npc.tob_maiden_dying_a".asRSCM(RSCMType.NPC)), models)
        fun advance() {
            boss.pendingSequence = EntitySeq.NULL
            boss.currentMapClock++
            boss.processedMapClock = boss.currentMapClock
            coroutine.advance()
        }
        repeat(firstTicks - 1) { advance() }
        assertFalse(complete)
        assertEquals(TheatreManager.ACTIVE, run.stage)
        advance()
        assertEquals(listOf(first, second), sequences)
        assertEquals(listOf("npc.tob_maiden_dying_a".asRSCM(RSCMType.NPC), "npc.tob_maiden_dying_b".asRSCM(RSCMType.NPC)), models)
        repeat(secondTicks - 1) { advance() }
        assertTrue(boss.isSlotAssigned)
        assertEquals(TheatreManager.ACTIVE, run.stage)
        advance()
        assertTrue(complete)
        assertFalse(boss.isSlotAssigned)
        assertEquals(TheatreManager.CLEARED, run.stage)
    }

    @Test
    fun `exit Orator preserves native model and movement data while adding Talk-to`() {
        val cache = Cache.load(Paths.get(".data", "cache", "LIVE"))
        try {
            val native = checkNotNull(cache.data(2, 9, "npc.tob_male_orator_vis".asRSCM(RSCMType.NPC)))
            val orator = checkNotNull(cache.data(2, 9, TheatreRoomObjects.ORATOR.asRSCM(RSCMType.NPC)))
            assertArrayEquals(native.dropLast(1).toByteArray(), orator.take(native.size - 1).toByteArray())
            assertArrayEquals(byteArrayOf(30) + "Talk-to".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0),
                orator.drop(native.size - 1).toByteArray())
        } finally { cache.close() }
    }

    @Test
    fun `Theatre healing keeps native purple artwork and can replace occupied damage slots`() {
        val cache = Cache.load(Paths.get(".data", "cache", "LIVE"))
        try {
            val native = checkNotNull(cache.data(2, 32, "hitmark.heal".asRSCM(RSCMType.HITMARK)))
            val heal = checkNotNull(cache.data(2, 32, "hitmark.theatre_heal".asRSCM(RSCMType.HITMARK)))
            assertArrayEquals(native.dropLast(1).toByteArray(), heal.dropLast(3).toByteArray())
            assertArrayEquals(byteArrayOf(12, 0, 0), heal.takeLast(3).toByteArray())
        } finally { cache.close() }
    }

    @Test
    fun `killed blood spawn finishes its death animation before removal`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val spawn = fixture.manager.spawn(run, "npc.maiden_blood_slug", run.roomrow.entry, 120)
        val sequences = mutableListOf<Int>()
        spawn.infoProtocol = object : NpcInfoProtocol by spawn.infoProtocol {
            override fun setSequence(seq: Int, delay: Int) { sequences += seq }
        }
        spawn.hitpoints = 0
        fixture.encounters.killed(run, spawn)
        fixture.encounters.killed(run, spawn)
        val id = "seq.tob_blood_spawn_death".asRSCM(RSCMType.SEQ)
        assertEquals(listOf(id), sequences)
        assertTrue(spawn.isSlotAssigned)
        assertTrue(spawn.movementLocked)
        repeat(checkNotNull(ServerCacheManager.getAnim(id)).tickDuration - 1) { fixture.advanceQueues() }
        assertTrue(spawn.isSlotAssigned)
        fixture.advanceQueues()
        assertFalse(spawn.isSlotAssigned)
        assertFalse(spawn in run.adds)
    }

    @Test
    fun `creating and joining a party places members outside the Maiden barrier`() {
        val fixture = Fixture()
        val leader = fixture.player().apply { displayName = "Leader" }
        val follower = fixture.player()
        fixture.enter(leader)
        val run = checkNotNull(fixture.manager.run(leader))
        assertEquals(TheatreManager.LOBBY, run.stage)
        assertEquals(TheatreData.rules.lobbyEntry, fixture.manager.source(run, leader.coords))
        assertFalse(run.roomrow.contains(fixture.manager.source(run, leader.coords)))
        fixture.enter(follower, "Leader")
        assertSame(run, fixture.manager.run(follower))
        assertEquals(leader.coords, follower.coords)
        fixture.manager.startEncounter(leader)
        assertEquals(TheatreManager.ACTIVE, run.stage)
        assertTrue(run.members.all { fixture.manager.source(run, it.coords) == TheatreData.rules.lobbyEntry })
    }

    @Test
    fun `Maiden tracks the player between attacks without moving from the pool`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.vars["varn.theatre_next_attack"] = 1000
        val pool = boss.coords
        val player = run.members.single()
        val facing = NpcFaceSquareProcessor()
        player.coords = fixture.manager.coord(run, CoordGrid(3175, 4440, 0))
        fixture.encounters.tick(run)
        assertEquals(player.coords, boss.pendingFaceSquare)
        facing.process(boss)
        val firstAngle = boss.pendingFaceAngle
        player.coords = fixture.manager.coord(run, CoordGrid(3175, 4452, 0))
        fixture.encounters.tick(run)
        facing.process(boss)
        assertNotEquals(firstAngle, boss.pendingFaceAngle)
        assertEquals(pool, boss.coords)
    }

    @Test
    fun `solo blood attack sends three distinct floor splats and no shadow attack`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val player = run.members.single()
        player.coords = fixture.manager.coord(run, CoordGrid(3178, 4447, 0))
        boss.vars["varn.theatre_attacks"] = TheatreData.mechanics.maidenBloodAttackCadence - 1
        run.tick = boss.vars["varn.theatre_next_attack"]
        fixture.updates.clear()
        fixture.encounters.tick(run)
        val projectiles = fixture.updates.updatedZones.values.flatMap { it.toList() }
            .filterIsInstance<MapProjAnimV2>()
        assertEquals(3, projectiles.size)
        assertTrue(projectiles.all { it.endHeight == 0 })
        assertTrue(projectiles.all { it.id == "spotanim.maiden_blood_proj".asRSCM(RSCMType.SPOTANIM) })
        val targets = projectiles.map { CoordGrid(it.end.x, it.end.z, it.end.level) }
        assertEquals(3, targets.distinct().size)
        assertTrue(player.coords in targets)
        assertTrue(targets.all { kotlin.math.abs(it.x - player.coords.x) <= 2 && kotlin.math.abs(it.z - player.coords.z) <= 2 })
        val bloodId = "loc.tob_maiden_blood".asRSCM(RSCMType.LOC)
        repeat(2) { fixture.advanceQueues() }
        assertTrue(targets.all { tile -> fixture.locationRepo.findAll(tile).none { it.id == bloodId } })
        fixture.advanceQueues()
        assertTrue(fixture.hits.isEmpty())
        assertTrue(targets.all { tile -> fixture.locationRepo.findAll(tile).none { it.id == bloodId } })
        val splashes = fixture.updates.updatedZones.int2ObjectEntrySet().flatMap { entry ->
            entry.value.toList().filterIsInstance<MapAnim>().filter {
                it.id == "spotanim.maiden_lingering_blood".asRSCM(RSCMType.SPOTANIM)
            }.map { ZoneKey(entry.intKey).toCoords().translate(it.xInZone, it.zInZone) }
        }
        assertEquals(targets.toSet(), splashes.toSet())
    }

    @Test
    fun `blood spawn starts at varied walkable arena positions instead of the player tile`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val player = run.members.single()
        player.coords = fixture.manager.coord(run, CoordGrid(3180, 4447, 0))
        val starts = mutableSetOf<CoordGrid>()
        repeat(12) {
            boss.vars["varn.theatre_attacks"] = TheatreData.mechanics.maidenBloodSpawnCadence - 1
            run.tick = boss.vars["varn.theatre_next_attack"]
            fixture.encounters.tick(run)
            val slug = run.adds.single { it.isType("npc.maiden_blood_slug") }
            starts += slug.coords
            assertTrue(run.roomrow.contains(fixture.manager.source(run, slug.coords)))
            assertFalse(fixture.flags.isWalkBlocked(slug.coords))
            assertEquals(run.tick, slug.vars["varn.theatre_birth_tick"])
            fixture.manager.remove(run, slug)
        }
        assertTrue(starts.size > 1)
        assertTrue(starts.any { it != player.coords })
    }

    @Test
    fun `fading blood splash damages the player without a trail object and expires with its graphic`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val player = run.members.single()
        player.coords = fixture.manager.coord(run, CoordGrid(3178, 4447, 0))
        boss.vars["varn.theatre_attacks"] = TheatreData.mechanics.maidenBloodAttackCadence - 1
        run.tick = boss.vars["varn.theatre_next_attack"]
        fixture.encounters.tick(run)
        boss.vars["varn.theatre_next_attack"] = Int.MAX_VALUE
        repeat(3) { fixture.advanceQueues() }
        val duration = checkNotNull(ServerCacheManager.getAnim("seq.tob_blood_splat".asRSCM(RSCMType.SEQ))).tickDuration
        val birth = run.tick
        run.tick += TheatreData.mechanics.maidenBloodDamageTicks - run.tick % TheatreData.mechanics.maidenBloodDamageTicks
        fixture.encounters.tick(run)
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
        val bloodId = "loc.tob_maiden_blood".asRSCM(RSCMType.LOC)
        assertTrue(fixture.locationRepo.findAll(player.coords).none { it.id == bloodId })
        fixture.hits.clear()
        run.tick = birth + duration
        run.tick += (TheatreData.mechanics.maidenBloodDamageTicks - run.tick % TheatreData.mechanics.maidenBloodDamageTicks) % TheatreData.mechanics.maidenBloodDamageTicks
        fixture.encounters.tick(run)
        assertTrue(fixture.hits.isEmpty())
    }

    @Test
    fun `fading splash and blood spawn trail share one damage hit and expire independently`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val player = run.members.single()
        player.coords = fixture.manager.coord(run, CoordGrid(3178, 4447, 0))
        boss.vars["varn.theatre_attacks"] = TheatreData.mechanics.maidenBloodAttackCadence - 1
        run.tick = boss.vars["varn.theatre_next_attack"]
        fixture.encounters.tick(run)
        boss.vars["varn.theatre_next_attack"] = Int.MAX_VALUE
        repeat(3) { fixture.advanceQueues() }
        val birth = run.tick
        val spawn = fixture.manager.spawn(run, "npc.maiden_blood_slug", fixture.manager.source(run, player.coords), 120)
        spawn.vars["varn.theatre_birth_tick"] = run.tick
        run.tick += TheatreData.mechanics.maidenBloodDamageTicks - run.tick % TheatreData.mechanics.maidenBloodDamageTicks
        fixture.encounters.tick(run)
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
        fixture.manager.remove(run, spawn)
        fixture.hits.clear()
        val duration = checkNotNull(ServerCacheManager.getAnim("seq.tob_blood_splat".asRSCM(RSCMType.SEQ))).tickDuration
        run.tick = birth + duration
        run.tick += (TheatreData.mechanics.maidenBloodDamageTicks - run.tick % TheatreData.mechanics.maidenBloodDamageTicks) % TheatreData.mechanics.maidenBloodDamageTicks
        fixture.encounters.tick(run)
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
        val bloodId = "loc.tob_maiden_blood".asRSCM(RSCMType.LOC)
        assertTrue(fixture.locationRepo.findAll(player.coords).any { it.id == bloodId })
        fixture.encounters.clear(run)
        fixture.hits.clear()
        fixture.encounters.tick(run)
        assertTrue(fixture.hits.isEmpty())
        assertTrue(fixture.locationRepo.findAll(player.coords).none { it.id == bloodId })
    }

    @Test
    fun `ordinary Maiden attack sends only the shadow projectile`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        run.tick = boss.vars["varn.theatre_next_attack"]
        fixture.updates.clear()
        fixture.encounters.tick(run)
        val projectiles = fixture.updates.updatedZones.values.flatMap { it.toList() }
            .filterIsInstance<MapProjAnimV2>()
        assertEquals(run.roomrow.projectile.asRSCM(RSCMType.SPOTANIM), projectiles.single().id)
        repeat(TheatreData.mechanics.maidenProjectileDelayTicks) { fixture.advanceQueues() }
        assertTrue(fixture.hits.isEmpty())
        fixture.advancePlayerHits()
        assertEquals(1, fixture.hits.size)
    }

    @Test
    fun `blood spawn keeps walking and leaves blood on every visited tile`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        checkNotNull(run.boss).vars["varn.theatre_next_attack"] = 1000
        val spawn = fixture.manager.spawn(run, "npc.maiden_blood_slug", CoordGrid(3180, 4447, 0), 120)
        spawn.moveRestrict = MoveRestrict.PassThru
        val bloodId = "loc.tob_maiden_blood".asRSCM(RSCMType.LOC)
        val visited = mutableSetOf<CoordGrid>()
        repeat(28) {
            run.tick++
            val before = spawn.coords
            visited += before
            fixture.encounters.tick(run)
            assertTrue(fixture.locationRepo.findAll(before).any { it.id == bloodId }, "Missing trail at $before")
            fixture.movement.process(spawn)
            assertNotEquals(before, spawn.coords, "Blood spawn paused on tick ${run.tick}")
        }
        assertTrue(visited.size > 8)
        assertTrue(visited.all { tile -> fixture.locationRepo.findAll(tile).any { it.id == bloodId } })
    }

    @Test
    fun `Maiden cannot pursue an attacker or open a second health widget`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val pool = boss.coords
        assertFalse(boss.canRetaliate())
        boss.walk(run.members.single().coords)
        fixture.movement.process(boss)
        assertEquals(pool, boss.coords)
        assertTrue(boss.routeDestination.isEmpty())
        assertEquals(0, boss.visType.paramOrNull(params.boss_hp_bar_mode))
    }

    @Test
    fun `Maiden healers spawn east and reach the pool using native room collision`() {
        val fixture = Fixture()
        val run = fixture.party(0, size = 5)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.hitpoints = boss.baseHitpointsLvl * 7 / 10
        fixture.encounters.tick(run)
        val healers = run.adds.filter { it.isType("npc.maiden_elemental") }
        assertEquals(10, healers.size)
        assertEquals(CoordGrid(3185, 4455, 0), fixture.manager.source(run, healers[0].coords))
        assertEquals(CoordGrid(3185, 4438, 0), fixture.manager.source(run, healers[1].coords))
        for ((index, healer) in healers.withIndex()) {
            val source = if (index % 2 == 0) TheatreData.mechanics.maidenHealerNorthDestination
                else TheatreData.mechanics.maidenHealerDestination
            val destination = fixture.manager.coord(run, source)
            assertEquals(source.packed, healer.vars["varn.theatre_healer_destination"])
            assertTrue(healer.coords.x > destination.x)
            assertFalse(healer.canRetaliate())
            repeat(30) { fixture.movement.process(healer) }
            assertEquals(destination, healer.coords, "Spawn $index: ${TheatreData.mechanics.maidenHealerSpawns[index]}")
            fixture.manager.remove(run, healer)
        }
    }

    @Test
    fun `Maiden absorbs remaining healer health once and lets its burst finish`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.hitpoints -= 100
        val healer = fixture.manager.spawn(run, "npc.maiden_elemental", TheatreData.mechanics.maidenHealerDestination, 37)
        healer.vars["varn.theatre_phase"] = 1
        val before = boss.hitpoints
        val marks = mutableListOf<Hitmark>()
        boss.infoProtocol = object : NpcInfoProtocol by boss.infoProtocol {
            override fun showHitmark(hitmark: Hitmark) { marks += hitmark }
        }
        fixture.encounters.tick(run)
        assertEquals(before + 74, boss.hitpoints)
        assertEquals(74, marks.single().damage)
        assertEquals("hitmark.theatre_heal".asRSCM(RSCMType.HITMARK), marks.single().public)
        assertEquals(1, boss.vars["varn.theatre_hp_boost"])
        assertTrue(healer.isSlotAssigned)
        assertTrue(healer.movementLocked)
        fixture.encounters.tick(run)
        assertEquals(before + 74, boss.hitpoints)
        assertEquals(1, boss.vars["varn.theatre_hp_boost"])
        val pendingHit = NpcHitEvents.BeforeImpact(healer, fixture.hit(run.members.single(), damage = 20))
        fixture.events.publish(pendingHit)
        assertEquals(0, pendingHit.damage)
        repeat(TheatreData.mechanics.maidenHealerBurstTicks - 1) { fixture.advanceQueues() }
        assertTrue(healer.isSlotAssigned)
        fixture.advanceQueues()
        assertFalse(healer.isSlotAssigned)
        assertFalse(healer in run.adds)
    }

    @Test
    fun `dead or distant Maiden healers cannot heal and absorption is capped`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.hitpoints -= 10
        val target = TheatreData.mechanics.maidenHealerDestination
        val dead = fixture.manager.spawn(run, "npc.maiden_elemental", target, 37)
        dead.vars["varn.theatre_phase"] = 1
        dead.hitpoints = 0
        val distant = fixture.manager.spawn(run, "npc.maiden_elemental", target.translate(2, 0), 37)
        distant.vars["varn.theatre_phase"] = 1
        fixture.encounters.tick(run)
        assertEquals(boss.baseHitpointsLvl - 10, boss.hitpoints)
        fixture.manager.remove(run, dead)
        distant.coords = fixture.manager.coord(run, target)
        fixture.encounters.tick(run)
        assertEquals(boss.baseHitpointsLvl, boss.hitpoints)
        assertEquals(1, boss.vars["varn.theatre_hp_boost"])
    }

    @Test
    fun `blood spawn wandering is independent of player position`() {
        fun destination(playerOffset: Int): org.rsmod.map.CoordGrid {
            val fixture = Fixture()
            val run = fixture.party(0)
            fixture.encounters.start(run)
            checkNotNull(run.boss).vars["varn.theatre_next_attack"] = 1000
            run.members.single().coords = fixture.manager.coord(run, run.roomrow.entry.translate(playerOffset, 0))
            val spawn = fixture.manager.spawn(run, "npc.maiden_blood_slug", run.roomrow.entry.translate(-3, 0), 120)
            run.tick = 1
            fixture.encounters.tick(run)
            assertFalse(spawn.canRetaliate())
            val target = checkNotNull(spawn.routeDestination.peekFirst())
            val source = fixture.manager.source(run, target)
            val bounds = run.roomrow.bounds
            assertTrue(source.x in bounds[0] + 1 until bounds[2])
            assertTrue(source.z in bounds[1] + 1 until bounds[3])
            val before = spawn.coords
            fixture.movement.process(spawn)
            assertNotEquals(before, spawn.coords)
            return source
        }
        assertEquals(destination(0), destination(-4))
    }

    @Test
    fun `blood spawn expiry removes only the expired actor without healing Maiden`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        boss.vars["varn.theatre_next_attack"] = 1000
        val older = fixture.manager.spawn(run, "npc.maiden_blood_slug", run.roomrow.entry.translate(-3, 0), 120)
        val newer = fixture.manager.spawn(run, "npc.maiden_blood_slug", run.roomrow.entry.translate(-4, 0), 120)
        older.vars["varn.theatre_birth_tick"] = 21
        newer.vars["varn.theatre_birth_tick"] = 22
        run.tick = 20 + TheatreData.mechanics.maidenBloodSpawnLifetimeTicks
        fixture.encounters.tick(run)
        assertTrue(older.isSlotAssigned)
        run.tick++
        fixture.encounters.tick(run)
        assertTrue(older.isSlotAssigned)
        assertEquals(2, older.vars["varn.theatre_phase"])
        assertTrue(older.movementLocked)
        assertTrue(older.routeDestination.isEmpty())
        val pendingHit = NpcHitEvents.BeforeImpact(older, fixture.hit(run.members.single(), damage = 20))
        fixture.events.publish(pendingHit)
        assertEquals(0, pendingHit.damage)
        val sequence = checkNotNull(ServerCacheManager.getAnim("seq.tob_blood_spawn_death".asRSCM(RSCMType.SEQ)))
        repeat(sequence.tickDuration - 1) { fixture.advanceQueues() }
        assertTrue(older.isSlotAssigned)
        fixture.advanceQueues()
        assertFalse(older.isSlotAssigned)
        assertFalse(older in run.adds)
        assertNull(fixture.manager.run(older))
        assertTrue(newer.isSlotAssigned)
        assertEquals(boss.baseHitpointsLvl, boss.hitpoints)
        assertEquals(0, boss.vars["varn.theatre_hp_boost"])
    }

    @Test
    fun `frozen Maiden adds resume their scripted routes after the freeze ends`() {
        val fixture = Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        val healer = fixture.manager.spawn(run, "npc.maiden_elemental", TheatreData.mechanics.maidenHealerSpawns.first(), 150)
        healer.vars["varn.theatre_phase"] = 1
        healer.vars["varn.freeze_end_clock"] = healer.currentMapClock + 10
        val start = healer.coords
        fixture.encounters.tick(run)
        fixture.movement.process(healer)
        assertEquals(start, healer.coords)
        assertTrue(healer.routeDestination.isEmpty())
        healer.vars["varn.freeze_end_clock"] = 0
        fixture.encounters.tick(run)
        fixture.movement.process(healer)
        assertNotEquals(start, healer.coords)
    }

    @Test
    fun `separate parties have distinct mapped rooms and independently owned boss actors`() {
        val fixture = Fixture()
        val a = fixture.party(0)
        val b = fixture.party(0)
        fixture.encounters.start(a)
        fixture.encounters.start(b)
        val first = checkNotNull(a.boss)
        val second = checkNotNull(b.boss)
        assertNotEquals(first.coords, second.coords)
        assertSame(a, fixture.manager.run(first))
        assertSame(b, fixture.manager.run(second))
        assertEquals(a.roomrow.center, fixture.manager.source(a, first.coords))
        assertEquals(b.roomrow.center, fixture.manager.source(b, second.coords))
        assertFalse(first.respawns)
        assertFalse(second.respawns)
    }

    @Test
    fun `registered impact handler rejects players belonging to another Theatre party`() {
        val fixture = Fixture()
        val a = fixture.party(0)
        val b = fixture.party(0)
        fixture.encounters.start(a)
        fixture.encounters.start(b)
        val boss = checkNotNull(a.boss)
        val local = NpcHitEvents.BeforeImpact(boss, fixture.hit(a.members.single(), damage = 23))
        val foreign = NpcHitEvents.BeforeImpact(boss, fixture.hit(b.members.single(), damage = 23))
        assertTrue(fixture.events.publish(local))
        assertTrue(fixture.events.publish(foreign))
        assertEquals(23, local.damage)
        assertEquals(0, foreign.damage)
    }

    @Test
    fun `delayed hazards cannot damage members of another party`() {
        val fixture = Fixture()
        val a = fixture.party(0)
        val b = fixture.party(0)
        fixture.encounters.start(a)
        fixture.encounters.start(b)
        val boss = checkNotNull(a.boss)
        fixture.manager.schedule(a, 2) {
            fixture.manager.damage(a, a.members.single(), 13, source = boss)
            fixture.manager.damage(a, b.members.single(), 29, source = boss)
        }
        fixture.advanceQueues()
        assertTrue(fixture.hits.isEmpty())
        fixture.advanceQueues()
        assertTrue(fixture.hits.isEmpty())
        fixture.advancePlayerHits()
        assertEquals(listOf(13), fixture.hits.map { it.damage })
    }

    @Test
    fun `room clear cancels delayed hazards without affecting the other party`() {
        val fixture = Fixture()
        val a = fixture.party(0)
        val b = fixture.party(0)
        fixture.encounters.start(a)
        fixture.encounters.start(b)
        val actors = a.adds.toList()
        var abandonedHazard = false
        var liveHazard = false
        fixture.manager.schedule(a, 1) { abandonedHazard = true }
        fixture.manager.schedule(b, 1) { liveHazard = true }
        val defeated = checkNotNull(a.boss)
        defeated.hitpoints = 0
        fixture.encounters.killed(a, defeated)
        fixture.manager.roomCleared(a)
        fixture.advanceQueues()
        assertFalse(abandonedHazard)
        assertTrue(liveHazard)
        assertEquals(TheatreManager.CLEARED, a.stage)
        assertEquals(1, a.members.single().vars[TheatreManager.ROOMS])
        assertTrue(actors.all { !it.isSlotAssigned && fixture.manager.run(it) == null })
        assertTrue(a.adds.isEmpty())
        assertNull(a.boss)
        assertTrue(fixture.manager.active(b))
        assertTrue(checkNotNull(b.boss).isSlotAssigned)
    }

    @Test
    fun `advancing before the old room callback executes starts exactly one new tick loop`() {
        val fixture = Fixture()
        val run = fixture.party(0, lobby = true)
        fixture.manager.startEncounter(run.members.single())
        fixture.manager.roomCleared(run)
        fixture.manager.advanceRoom(run.members.single())
        assertEquals(1, run.room)
        assertEquals(TheatreManager.WAITING, run.stage)
        fixture.manager.startEncounter(run.members.single())
        assertEquals(0, run.tick)
        fixture.advanceQueues()
        assertEquals(1, run.tick)
        fixture.advanceQueues()
        assertEquals(2, run.tick)
    }

    @Test
    fun `registered Nylocas launch filtering locks only the attacking party member`() {
        val fixture = Fixture()
        val run = fixture.party(2, size = 2)
        fixture.nylocas.start(run)
        run.tick = TheatreData.rules.nylocasFirstWaveTicks
        fixture.nylocas.tick(run)
        val npc = run.adds.first { it.vars["varn.theatre_nylo_pillar_index"] == 0 }
        val style = TheatreAttackStyle.entries[npc.vars["varn.theatre_nylo_style"]]
        val correct = fixture.type(style)
        val wrong = fixture.type(TheatreMechanics.nextNyloStyle(style, 0))
        val mistake = fixture.builder(run.members[0], wrong, 7)
        assertTrue(fixture.events.publish(NpcHitEvents.AnyModify(npc, mistake)))
        assertEquals(0, mistake.damage)
        val teammate = fixture.builder(run.members[1], correct, 7)
        fixture.events.publish(NpcHitEvents.AnyModify(npc, teammate))
        assertEquals(7, teammate.damage)
        val repeat = fixture.builder(run.members[0], correct, 7)
        fixture.events.publish(NpcHitEvents.AnyModify(npc, repeat))
        assertEquals(0, repeat.damage)
    }

    @Test
    fun `queued correct Nylocas attack survives a style change before impact`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        run.tick = TheatreData.rules.nylocasFirstWaveTicks
        fixture.nylocas.tick(run)
        val npc = run.adds.first { it.vars["varn.theatre_nylo_pillar_index"] == 0 }
        val style = TheatreAttackStyle.entries[npc.vars["varn.theatre_nylo_style"]]
        val launched = fixture.builder(run.members.single(), fixture.type(style), 5)
        fixture.events.publish(NpcHitEvents.AnyModify(npc, launched))
        npc.vars["varn.theatre_nylo_style"] = TheatreMechanics.nextNyloStyle(style, 0).ordinal
        val impact = NpcHitEvents.BeforeImpact(npc, launched.build())
        assertTrue(fixture.events.publish(impact))
        assertEquals(5, impact.damage)
    }

    @Test
    fun `Nylocas boss intro blocks damage and scheduled reveal enables the correct style`() {
        val fixture = Fixture()
        val run = fixture.party(2)
        fixture.nylocas.start(run)
        run.controller.vars["varn.theatre_nylo_wave"] = 31
        run.tick = 1
        fixture.nylocas.tick(run)
        assertNull(run.boss)
        run.tick = run.controller.vars["varn.theatre_nylo_boss_spawn_tick"]
        fixture.nylocas.tick(run)
        val boss = checkNotNull(run.boss)
        val intro = NpcHitEvents.BeforeImpact(
            boss, fixture.hit(run.members.single(), HitType.Melee, 10),
        )
        assertTrue(fixture.events.publish(intro))
        assertEquals(0, intro.damage)
        assertTrue(boss.ignoreCombatInteractions)
        repeat(TheatreData.rules.nylocasBossIntroTicks) { fixture.advanceQueues() }
        assertFalse(boss.canRetaliate())
        val revealed = NpcHitEvents.BeforeImpact(
            boss, fixture.hit(run.members.single(), HitType.Melee, 10),
        )
        assertTrue(fixture.events.publish(revealed))
        assertEquals(10, revealed.damage)
        assertEquals(2, run.controller.vars["varn.theatre_nylo_boss_active"])
    }

    @Test
    fun `lethal hits transition both Verzik phases without ending the room early`() {
        val fixture = Fixture()
        val run = fixture.party(5)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        repeat(2) { phase ->
            boss.hitpoints = 1
            val event = NpcHitEvents.BeforeImpact(
                boss, fixture.hit(run.members.single(), HitType.Melee, 10),
            )
            assertTrue(fixture.events.publish(event))
            assertEquals(0, event.damage)
            assertEquals(phase + 1, run.phase)
            assertTrue(boss.hitpoints > 1)
            assertTrue(fixture.manager.active(run))
            assertSame(boss, run.boss)
        }
        boss.hitpoints = 1
        val finalHit = NpcHitEvents.BeforeImpact(
            boss, fixture.hit(run.members.single(), HitType.Melee, 10),
        )
        assertTrue(fixture.events.publish(finalHit))
        assertEquals(10, finalHit.damage)
        assertEquals(2, run.phase)
    }

    @Test
    fun `blood Nylocas heals Verzik between boss attack ticks`() {
        val fixture = Fixture()
        val run = fixture.party(5)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        run.phase = 1
        run.tick = 1
        boss.hitpoints = boss.baseHitpointsLvl - 100
        boss.vars["varn.theatre_next_attack"] = 20
        val blood = fixture.manager.spawn(
            run, "npc.tob_verzik_phase2_bloodnylocas", run.roomrow.center.translate(3, 0), 37,
        )
        val before = boss.hitpoints
        fixture.encounters.tick(run)
        assertEquals(before + 37, boss.hitpoints)
        assertFalse(blood.isSlotAssigned)
        assertFalse(blood in run.adds)
        assertTrue(fixture.hits.isEmpty())
        assertEquals(20, boss.vars["varn.theatre_next_attack"])
    }

    @Test
    fun `personal tornado contact damages its member between boss attack ticks`() {
        val fixture = Fixture()
        val run = fixture.party(5, size = 2)
        fixture.encounters.start(run)
        val boss = checkNotNull(run.boss)
        val victim = run.members[1]
        run.phase = 2
        run.tick = 1
        boss.hitpoints = boss.baseHitpointsLvl - 300
        boss.vars["varn.theatre_next_attack"] = 20
        val tornado = fixture.manager.spawn(
            run, "npc.tob_verzik_creeper", fixture.manager.source(run, victim.coords), 1,
        )
        tornado.vars["varn.theatre_target_member"] = 1
        val before = boss.hitpoints
        fixture.encounters.tick(run)
        fixture.advancePlayerHits()
        assertEquals(listOf(49), fixture.hits.map { it.damage })
        assertEquals(before + 147, boss.hitpoints)
        assertFalse(tornado.isSlotAssigned)
        assertFalse(tornado in run.adds)
        assertEquals(0, run.members[0].vars["varp.theatre_tornado_next"])
        assertEquals(
            1 + TheatreData.mechanics.verzikTornadoRespawnTicks,
            victim.vars["varp.theatre_tornado_next"],
        )
        assertEquals(20, boss.vars["varn.theatre_next_attack"])
        run.tick++
        fixture.encounters.tick(run)
        assertEquals(1, fixture.hits.size)
    }

    internal class Fixture(seed: Long = 1) {
        val clock = MapClock(100)
        val events = EventBus()
        val flags = CollisionFlagMap()
        val movement = NpcMovementProcessor(flags, StepFactory(flags), events)
        val players = PlayerList()
        val npcs = NpcList()
        val hits = mutableListOf<HitBuilder>()
        private val random = DefaultGameRandom(seed)
        private val queues = WorldQueueList()
        private val npcRegistry = NpcRegistry(npcs, flags, events)
        val updates = ZoneUpdateMap()
        private val locs = LocZoneStorage()
        private val normalLocs = LocRegistryNormal(updates, flags, locs)
        val zoneActivity = ZonePlayerActivityBitSet()
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normalLocs,
            flags, locs, npcRegistry, ControllerRegistry(clock, ControllerList()),
            zoneActivity,
        )
        private val repo = NpcRepository(clock, npcRegistry, npcs)
        val instances = InstanceManager(
            RegionRepository(regions), repo, players, events, InstanceAreaResolver(), clock, flags,
        )
        val inventory = TheatreInventory(random)
        val locationRepo = LocRepository(
            clock,
            LocRegistry(locs, normalLocs, LocRegistryRegion(updates, flags, locs, regions)),
            regions,
        )
        val objectRepo = ObjRepository(clock, ObjRegistry(updates))
        val roomObjects = TheatreRoomObjects(
            instances, locationRepo, objectRepo, flags, inventory, repo,
        )
        private val music = MusicPlayer::class.java.getDeclaredConstructor(
            GameRandom::class.java, MusicRepository::class.java,
        ).apply { isAccessible = true }
            .newInstance(random, MusicRepository(random).apply { load() })
        private val accuracy by lazy {
            val bonuses = WornBonuses()
            val styles = AttackStyles().apply {
                javaClass.declaredMethods.single { it.name.startsWith("startup") }
                    .apply { isAccessible = true }.invoke(this)
            }
            AccuracyFormulae(
                NvPMagicAccuracy(bonuses, styles), unused(), unused(), unused(),
                NvPMeleeAccuracy(bonuses, styles), unused(), unused(), unused(),
                NvPRangedAccuracy(bonuses, styles), unused(), unused(), unused(),
                unused(), unused(), unused(), unused(),
            )
        }
        private val deps = BossDeps(
            random, accuracy, WorldRepository(updates), repo, players, clock,
            queues, flags, EncounterRegistry(clock), BossExtensionRegistry(),
            PlayerHitModifier { hits += this },
            locRepo = locationRepo,
        )
        val presentation = TheatrePresentation(music)
        val parties = TheatreParties()
        val manager: TheatreManager = TheatreManager(
            deps, instances, inventory, unused<PlayerDeathDrops>(), events,
            Provider { encounters }, Provider { nylocas }, presentation, roomObjects, parties,
        )
        val encounters by lazy { TheatreEncounters(manager, StepFactory(flags), TheatreBloat(manager, RayCastValidator(flags))) }
        val nylocas by lazy { TheatreNylocas(Provider { manager }, RayCastValidator(flags), BoundValidator(flags)) }

        init {
            val exit = TheatreData.rules.exit
            flags.allocateIfAbsent(exit.x, exit.z, exit.level)
            val builder = GameMapBuilder()
            for (region in TheatreData.rules.regions) {
                for (x in (region shr 8) * 64 until ((region shr 8) + 1) * 64 step 8) {
                    for (z in (region and 255) * 64 until ((region and 255) + 1) * 64 step 8) {
                        repeat(4) { level -> flags.allocateIfAbsent(x, z, level) }
                    }
                }
            }
            for (source in nativeMaps) {
                GameMapDecoder.putMaps(flags, source.square, source.tiles)
                GameMapDecoder.putLocs(builder, flags, source.square, source.tiles, source.locs)
            }
            for ((zone, locations) in builder.zoneBuilders) {
                locs.mapLocs[zone] = locations.build()
            }
            with(TheatreScript(manager, inventory, encounters, nylocas)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun party(room: Int, size: Int = 1, lobby: Boolean = false): TheatreRun {
            val members = List(size) { player() }
            members.first().displayName = "Party${members.first().uuid}"
            parties.create(members.first())
            members.drop(1).forEach { parties.join(it, members.first().displayName) }
            val entry = if (lobby) TheatreData.rules.lobbyEntry else TheatreData.roomsList[room].entry
            val area = InstanceArea.copyRegions(
                TheatreData.rules.regions,
                enterCoord = RegionLocal(entry.level, entry.mx, entry.mz, entry.lx, entry.lz),
                exitCoord = TheatreData.rules.exit,
            )
            val spec = InstanceSettings(maxPlayers = 5, destroyWhenEmpty = false)
                .withArea(area, TheatreData.rules.rowId)
            val created = instances.create(
                members.first(), TheatreManager.KEY, spec, InstanceAccess.Private, clock.cycle,
            ) as InstanceManager.Result.Created
            for (member in members) {
                member.coords = created.enter
                instances.finalizeEntry(member, created.session, clock.cycle)
            }
            val controller = Npc("npc.tob_sotetseg_creeper", created.enter)
            repo.add(controller, Int.MAX_VALUE)
            controller.respawns = false
            instances.attachNpc(created.session.id, controller)
            val run = TheatreRun(created.session, controller)
            run.members += members
            run.room = room
            run.teamSize = size
            run.stage = if (lobby) TheatreManager.LOBBY else TheatreManager.ACTIVE
            checkNotNull(parties.get(members.first())).apply {
                session = created.session
                this.run = run
            }
            val field = TheatreManager::class.java.getDeclaredField("runs")
                .apply { isAccessible = true }
            (field.get(manager) as MutableMap<InstanceId, TheatreRun>)[created.session.id] = run
            return run
        }

        fun player(): Player {
            val slot = checkNotNull(players.nextFreeSlot())
            return Player().apply {
                slotId = slot
                uuid = slot.toLong()
                observerUUID = uuid
                assignUid()
                client = NoopClient
                currentMapClock = clock.cycle
                inv = Inventory.create("inv.inv")
                worn = Inventory.create("inv.worn")
                invMap["inv.inv"] = inv
                invMap["inv.worn"] = worn
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setBaseLevel("stat.prayer", 99)
                statMap.setCurrentLevel("stat.prayer", 99)
                players[slot] = this
            }
        }

        fun enter(player: Player, leader: String? = null) {
            val context = ProtectedAccessContextFactory.empty().copy(
                getCollision = { flags }, getEventBus = { events },
            )
            player.processedMapClock = player.currentMapClock
            if (parties.get(player) == null) {
                if (leader == null) parties.create(player) else parties.join(player, leader)
            }
            player.launch { manager.enter(ProtectedAccess(player, this, context)) }
            repeat(15) {
                if (player.activeCoroutine?.isSuspended != true) return
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                player.lastMapBuildComplete = player.currentMapClock
                player.activeCoroutine?.advance()
            }
            error("Theatre entry did not finish")
        }

        fun advanceQueues() {
            advancePlayerHits()
            val iterator = queues.iterator()
            while (iterator.hasNext()) {
                val queue = iterator.next()
                queue.remainingCycles--
                if (queue.remainingCycles <= 0) {
                    iterator.remove()
                    queue.action()
                }
            }
            iterator.cleanUp()
        }

        fun advancePlayerHits() {
            val impactQueue = "queue.impact_hit".asRSCM(RSCMType.QUEUE)
            for (player in players) {
                val iterator = player.queueList.iterator() ?: continue
                while (iterator.hasNext()) {
                    val queue = iterator.next()
                    if (queue.id != impactQueue) continue
                    queue.remainingCycles--
                    if (queue.remainingCycles > 0) continue
                    iterator.remove()
                    val deferred = queue.args as DeferredPlayerHit
                    with(deferred.modifier) { deferred.builder.modify(player) }
                }
                iterator.cleanUp()
            }
        }

        fun type(style: TheatreAttackStyle): HitType = when (style) {
            TheatreAttackStyle.MELEE -> HitType.Melee
            TheatreAttackStyle.RANGED -> HitType.Ranged
            TheatreAttackStyle.MAGIC -> HitType.Magic
        }

        fun hit(player: Player, type: HitType = HitType.Melee, damage: Int): Hit =
            builder(player, type, damage).build()

        fun builder(player: Player, type: HitType, damage: Int): HitBuilder = HitBuilder(
            type, damage, player.uid.packed, player.slotId, false, true, 0, null, null,
            0, 0, null, null, null, null, Int.MAX_VALUE, Int.MAX_VALUE,
        )
    }

    companion object {
        private val nativeMaps: List<NativeMap> by lazy {
            val cache = Cache.load(Paths.get(".data", "cache", "SERVER"))
            try {
                TheatreData.rules.regions.mapNotNull { region ->
                    val tiles = cache.data(5, region, 0) ?: return@mapNotNull null
                    val locs = cache.data(5, region, 1) ?: return@mapNotNull null
                    NativeMap(
                        MapSquareKey(region shr 8, region and 255),
                        MapTileDecoder.decode(InlineByteBuf(tiles)),
                        MapLocListDecoder.decode(InlineByteBuf(locs)),
                    )
                }
            } finally {
                cache.close()
            }
        }

        @JvmStatic @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }

        private data class NativeMap(
            val square: MapSquareKey,
            val tiles: MapTileSimpleDefinition,
            val locs: MapLocListDefinition,
        )

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
                .apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
