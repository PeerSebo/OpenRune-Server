package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.loc.MapLocListDefinition
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
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
import org.rsmod.api.repo.EntityLifecycleProcess
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.content.minigames.gotr.scripts.GotrGuardianScript
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GotrDefenceIntegrationTest {
    private lateinit var map: MapTileSimpleDefinition
    private lateinit var mapLocs: MapLocListDefinition
    private lateinit var mapNpcs: List<MapNpcDefinition>

    @BeforeAll fun loadNativeArena() {
        val cache = ServerCacheManager.init(241)
        try {
            val group = (56 shl 8) or 148
            map = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
            mapLocs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
            val packed = MapNpcListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 5))))
            mapNpcs = packed.packedSpawns.toIntArray().map(::MapNpcDefinition)
        } finally {
            cache.close()
        }
    }

    @Test fun `first wave spawns at 201 and walks to damage the native central barrier`() {
        val f = Fixture()
        f.enter()
        val use = checkNotNull(f.defence.placeCell(FRONT_TILE, 1))
        val barrier = f.barrierAt(FRONT_WALL)
        assertEquals(use.hitpoints, barrier.hitpoints)
        f.advanceTo(200)
        assertEquals(0, f.defence.creatureCount())
        f.step()
        val wave = f.creatures()
        assertEquals(4, wave.size)
        assertEquals(setOf(3611, 3612, 3613, 3614), wave.map { it.coords.x }.toSet())
        assertTrue(wave.all { it.coords.z == 9518 })
        f.until(60) { barrier.hitpoints < use.hitpoints }
        assertTrue(wave.any { f.walked(it) >= 3 }, f.positions())
        assertEquals(25.0, f.game.power, "The central barrier should absorb these first attacks.")
    }

    @Test fun `assembled guardian walks through native lanes and engages an abyssal creature`() {
        val f = Fixture()
        f.enter()
        assertTrue(f.defence.assemble(4, catalytic = true))
        val guardian = f.npcs.single { it.type.id == "npc.gotr_guardian_blood".asRSCM() }
        f.advanceTo(200)
        assertTrue(f.walked(guardian) >= 10, "Guardian never left the assembly: ${guardian.coords}")
        f.until(70) { f.creatures().any { it.hitpoints < it.baseHitpointsLvl || it.lifecycleDelCycle > 0 } }
        f.step()
        assertTrue(f.defence.guardianCount() > 0)
        assertTrue(f.creatures().any { f.walked(it) > 0 }, f.positions())
        assertEquals(0, f.player.gotrElementalPoints, "NPC combat must not create loot points.")
    }

    @Test fun `native Great Guardian spawn is stationary at the arena centre`() {
        val f = Fixture()
        val great = f.npcs.single { it.id == "npc.gotr_great_guardian".asRSCM() }
        assertEquals(CoordGrid(3613, 9501), great.coords)
        assertEquals(NpcMode.None, great.mode)
        assertEquals(MoveRestrict.NoMove, great.moveRestrict)
        f.enter()
        great.walk(great.coords.translate(1, 0))
        f.advanceTo(200)
        assertEquals(CoordGrid(3613, 9501), great.coords)
    }

    @Test fun `leeches attacking the Great Guardian switch to a newly built shield`() {
        val f = Fixture()
        f.enter()
        f.until(400) { f.game.power < 25.0 }
        val use = checkNotNull(f.defence.placeCell(FRONT_TILE, 4))
        val barrier = f.barrierAt(FRONT_WALL)
        val power = f.game.power
        f.until(30) { barrier.hitpoints < use.hitpoints }
        assertEquals(power, f.game.power, "Leeches must stop damaging the Guardian while switching to the shield.")
    }

    @Test fun `each abyssal species can walk its native footprint to the Great Guardian`() {
        for ((roll, npcType) in listOf(0 to 11405, 15 to 11406, 99 to 11407)) {
            val f = Fixture(speciesRoll = roll)
            f.enter()
            f.until(400) { f.game.power < 25.0 }
            val creatures = f.creatures()
            assertTrue(creatures.isNotEmpty(), "Species $npcType failed before reaching the Guardian.")
            assertTrue(creatures.all { it.type.id == npcType }, f.positions())
            assertTrue(creatures.any { f.walked(it) >= 8 }, f.positions())
        }
    }

    @Test fun `broken front barrier admits creatures to Great Guardian while a rear barrier survives`() {
        val f = Fixture()
        f.enter()
        checkNotNull(f.defence.placeCell(FRONT_TILE, 1))
        repeat(4) { checkNotNull(f.defence.placeCell(REAR_TILE, 4)) }
        val rear = f.barrierAt(REAR_WALL)
        f.until(400) { f.game.power < 25.0 }
        assertTrue(f.defence.needsRepair(FRONT_TILE), "The front wall was not destroyed: ${f.positions()}")
        assertTrue(rear.isSlotAssigned && rear.hitpoints > 0, "An unrelated rear barrier must not be required to die first.")
        assertTrue(f.creatures().any { it.coords.z < FRONT_WALL.z }, f.positions())
        assertTrue(f.creatures().any { f.walked(it) >= 8 }, f.positions())
    }

    @Test fun `creatures cause failure without payout and restart removes old actors and resets energy`() {
        val f = Fixture()
        f.enter()
        f.player.inv[0] = InvObj("obj.gotr_guardian_fragment", 300)
        f.player.inv[1] = InvObj(EssencePouch.GUARDIAN_ESSENCE)
        f.player.inv[2] = InvObj("obj.airrune", 50)
        EssencePouch.setStoredEssenceType(f.player, EssencePouch.Tier.Small, EssencePouch.GUARDIAN_ESSENCE)
        EssencePouch.depositUpTo(f.player, EssencePouch.Tier.Small, 3)
        f.game.rewardCell(f.player, 200, 200, 0.0)
        val xpBefore = f.player.statMap.getFineXP("stat.runecrafting")
        f.until(400) { f.game.phase == GotrPhase.LOBBY }
        val failedAt = f.clock.cycle
        assertEquals(0.0, f.game.power)
        assertEquals(0, f.defence.creatureCount())
        assertEquals(0, f.defence.guardianCount())
        assertTrue(f.creatures().isEmpty())
        assertEquals(0, f.player.gotrElementalPoints)
        assertEquals(0, f.player.gotrCatalyticPoints)
        assertEquals(0, f.player.gotrRiftsClosed)
        assertEquals(xpBefore, f.player.statMap.getFineXP("stat.runecrafting"))
        assertEquals(1, f.player.vars["varbit.gotr_player_has_finished_game"])
        assertEquals(0, f.player.inv.count("obj.gotr_guardian_fragment"))
        assertEquals(0, f.player.inv.count(EssencePouch.GUARDIAN_ESSENCE))
        assertEquals(0, EssencePouch.storedAmountForType(f.player, EssencePouch.GUARDIAN_ESSENCE))
        assertEquals(50, f.player.inv.count("obj.airrune"))
        f.until(60) { f.game.phase == GotrPhase.ACTIVE }
        assertEquals(50, f.clock.cycle - failedAt)
        assertEquals(10, f.game.powerPercent())
        assertEquals(0, f.game.activeElapsedTicks())
        assertEquals(0, f.player.vars["varbit.gotr_elemental_earned_this_game"])
        assertEquals(0, f.player.vars["varbit.gotr_catalytic_earned_this_game"])
        assertTrue(f.creatures().isEmpty())
        f.advanceTo(200)
        assertTrue(f.creatures().isEmpty())
        f.step()
        assertEquals(4, f.defence.creatureCount())
    }

    @Test fun `full charge ends attacks before their next hit and normal lifecycle removes bodies`() {
        val f = Fixture()
        f.enter()
        f.until(400) { f.game.power < 25.0 }
        // Charge between leech attacks so the next late cycle tests victory before another hit.
        f.step()
        assertEquals(GotrPhase.ACTIVE, f.game.phase)
        val attackers = f.creatures().toList()
        repeat((f.game.target() - f.game.power).toInt()) {
            assertTrue(f.game.applyGuardianStone(f.player, GotrGame.STONE_ELEMENTAL))
        }
        f.step()
        assertEquals(GotrPhase.GRACE, f.game.phase)
        assertEquals(100, f.game.powerPercent())
        assertEquals(0, f.defence.creatureCount())
        repeat(4) { f.step() }
        assertTrue(attackers.none { it.isSlotAssigned }, "The actual lifecycle must delete dying creatures.")
        assertEquals(100, f.game.powerPercent())
    }

    private inner class Fixture(private val speciesRoll: Int = 99) {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        private val controllers = ControllerList()
        private val npcRegistry = NpcRegistry(npcs, collision, events)
        private val controllerRegistry = ControllerRegistry(clock, controllers)
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            npcRegistry, controllerRegistry, ZonePlayerActivityBitSet())
        private val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        private val locRepo = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        private val lifecycle = EntityLifecycleProcess(npcRepo, locRepo, ObjRepository(clock, ObjRegistry(updates)),
            ControllerRepository(controllerRegistry, controllers))
        private val movement = NpcMovementProcessor(collision, StepFactory(collision), events)
        private val post = NpcPostTickProcess(npcs, npcRegistry, NpcFaceSquareProcessor(),
            GameExceptionHandler { error, message -> throw AssertionError(message().toString(), error) })
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = if (maxExclusive == 100) speciesRoll else maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive
            override fun randomDouble() = 0.99
        }
        val defence = GotrDefence(locRepo, npcRepo, clock, random, RouteFactory(collision), RayCastValidator(collision), players, collision)
        val game = GotrGame(locRepo, players, clock, random, defence, GotrHud(events), GotrPortalPresentation(locRepo, WorldRepository(updates)))
        private val paths = mutableMapOf<Npc, MutableList<CoordGrid>>()
        private val client = object : Client<Any, Any> {
            override fun write(message: Any) {}
            override fun close() {}
            override fun read(player: Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: Player) {}
        }
        val player = Player(client).apply {
            uuid = 1; slotId = 1; displayName = "Defender"; currentMapClock = clock.cycle
            coords = CoordGrid(3615, 9491)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.runecrafting", 99)
            statMap.setCurrentLevel("stat.runecrafting", 99)
            players[1] = this
        }

        init {
            with(GotrGuardianScript(game)) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            val square = MapSquareKey(56, 148)
            for (level in 0..3) for (x in 3584 until 3648 step 8) for (z in 9472 until 9536 step 8) {
                collision.allocateIfAbsent(x, z, level)
            }
            GameMapDecoder.putMaps(collision, square, map)
            val builder = GameMapBuilder()
            GameMapDecoder.putLocs(builder, collision, square, map, mapLocs)
            for ((key, zone) in builder.zoneBuilders) storage.mapLocs[key] = zone.build()
            assertTrue(mapLocs.spawns.size > 100, "The fixture must contain the packed scenery, not an empty arena.")
            for (tile in GotrDefence.CELL_TILES) assertNotNull(locRepo.findExact(tile, LocShape.GroundDecor), "Missing native cell at $tile")
            val great = mapNpcs.single { it.id == "npc.gotr_great_guardian".asRSCM() }
            val coords = square.toCoords(great.level).translate(great.localX, great.localZ)
            assertEquals(CoordGrid(3613, 9501), coords)
            npcRepo.add(Npc("npc.gotr_great_guardian", coords), Int.MAX_VALUE)
        }

        fun enter() { game.start(); assertEquals(GotrEnterResult.Allowed, game.tryEnter(player)) }

        fun step() {
            clock.cycle++
            player.currentMapClock = clock.cycle
            lifecycle.process()
            for (npc in npcs.toList()) {
                npc.currentMapClock = clock.cycle
                npc.processedMapClock = clock.cycle
                npc.previousCoords = npc.coords
                val before = npc.coords
                movement.process(npc)
                assertTrue(before.chebyshevDistance(npc.coords) <= 2, "Movement bypassed stepping: $before -> ${npc.coords}")
                val path = paths.getOrPut(npc) { mutableListOf(before) }
                path += npc.coords
            }
            post.process()
            game.tick()
        }

        fun advanceTo(elapsed: Int) { while (game.activeElapsedTicks() < elapsed) step() }
        fun until(maxTicks: Int, condition: () -> Boolean) {
            repeat(maxTicks) { if (condition()) return; step() }
            assertTrue(condition(), "Condition not reached in $maxTicks ticks: phase=${game.phase}, power=${game.power}; ${positions()}")
        }
        fun creatures() = npcs.filter { it.type.id in 11405..11407 }
        fun barrierAt(coords: CoordGrid) = npcs.single { it.coords == coords && it.type.id in 11418..11425 }
        fun walked(npc: Npc): Int = paths[npc]?.zipWithNext()?.sumOf { (a, b) -> a.chebyshevDistance(b) } ?: 0
        fun positions(): String = npcs.joinToString { "${it.type.id}@${it.coords}, hp=${it.hitpoints}, steps=${walked(it)}" }
    }

    private companion object {
        val FRONT_TILE = CoordGrid(3615, 9510)
        val FRONT_WALL = CoordGrid(3614, 9511)
        val REAR_TILE = CoordGrid(3622, 9503)
        val REAR_WALL = CoordGrid(3623, 9502)
    }
}
