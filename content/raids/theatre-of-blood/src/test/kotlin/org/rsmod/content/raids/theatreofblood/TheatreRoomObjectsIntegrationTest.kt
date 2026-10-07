package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator

@ResourceLock("ServerCacheManager")
class TheatreRoomObjectsIntegrationTest {
    @Test fun `final room uses its walkable foyer and Orator readiness while the native barrier resigns safely`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(4)
        world.manager.roomCleared(run)
        val player = run.members.single()
        player.worn[3] = InvObj("obj.bronze_dagger")
        world.manager.advanceRoom(player)
        assertEquals(5, run.room)
        assertEquals(TheatreManager.WAITING, run.stage)
        assertEquals(CoordGrid(3168, 4304, 0), world.manager.source(run, player.coords))
        assertNull(run.boss)
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { world.events }, getCollision = { world.flags }, getAlignment = { TextAlignment() },
        )
        val orator = exits(world, run).single()
        player.processedMapClock = player.currentMapClock
        player.launch { world.events.publish(ProtectedAccess(player, this, context), NpcEvents.Op1(orator)) }
        player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
        assertEquals(TheatreManager.ACTIVE, run.stage)
        assertNotNull(run.boss)
        val location = world.locationRepo.findAll(world.manager.coord(run, run.roomrow.barriers[1]))
            .first { it.id == "loc.tob_walkway_verzik_barrier_op".asRSCM(RSCMType.LOC) }
        val type = checkNotNull(ServerCacheManager.getObject(location.id))
        val barrier = BoundLocInfo(location, type)
        player.launch { world.events.publish(ProtectedAccess(player, this, context), LocEvents.Op1(barrier, barrier, type)) }
        player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 2))
        repeat(2) {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.activeCoroutine?.advance()
        }
        assertNull(world.manager.run(player))
        assertEquals(TheatreData.rules.exit, player.coords)
        assertEquals(InvObj("obj.bronze_dagger"), player.worn[3])
        assertEquals(0, player.vars[TheatreManager.DEATHS])
        assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
    }

    @Test fun `Maiden barrier walks a fighting player outside without abandoning or losing equipment`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(0)
        fixture.encounters.start(run)
        fixture.roomObjects.enterRoom(run)
        val player = run.members.single()
        player.coords = fixture.manager.coord(run, CoordGrid(3184, 4447, 0))
        player.worn[3] = InvObj("obj.bronze_dagger")
        val type = checkNotNull(ServerCacheManager.getObject("loc.tob_arena_barrier".asRSCM(RSCMType.LOC)))
        val barrier = BoundLocInfo(LocInfo(2, fixture.manager.coord(run, CoordGrid(3185, 4447, 0)), LocEntity(type.id, 10, 1)), type)
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { fixture.events }, getCollision = { fixture.flags }, getAlignment = { TextAlignment() },
        )
        player.processedMapClock = player.currentMapClock
        player.launch {
            fixture.events.publish(ProtectedAccess(player, this, context), LocEvents.Op1(barrier, barrier, type))
        }
        assertFalse(player.forcedRoute)
        player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
        assertTrue(player.forcedRoute)
        val movement = PlayerMovementProcessor(fixture.flags, RouteFactory(fixture.flags), StepFactory(fixture.flags), fixture.events)
        repeat(4) {
            movement.process(player)
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.activeCoroutine?.advance()
        }
        assertEquals(CoordGrid(3186, 4447, 0), fixture.manager.source(run, player.coords))
        assertFalse(player.forcedRoute)
        assertTrue(player.routeDestination.isEmpty())
        assertSame(run, fixture.manager.run(player))
        assertEquals(TheatreManager.ACTIVE, run.stage)
        assertEquals(InvObj("obj.bronze_dagger"), player.worn[3])
        assertTrue(fixture.inventory.recovery(player).isEmpty())
    }

    @Test fun `lobby Vyre Orator is reachable without crossing the arena barrier`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(0, lobby = true)
        fixture.roomObjects.enterRoom(run)
        val entry = run.members.single().coords
        val nearby = reachable(fixture, entry, 45)
        val orator = exits(fixture, run).single()
        assertEquals(CoordGrid(3192, 4447, 0), fixture.manager.source(run, orator.coords))
        assertTrue(perimeter(orator.coords, orator.size).any { it in nearby })
        assertFalse(run.roomrow.contains(fixture.manager.source(run, orator.coords)))
        fixture.manager.startEncounter(run.members.single())
        assertEquals(TheatreManager.ACTIVE, run.stage)
        assertEquals(1, exits(fixture, run).size)
    }

    @Test fun `all six rooms have an exit and advancing removes the preceding exit`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(0, size = 2)
        var previous: Npc? = null
        for (room in TheatreData.roomsList.indices) {
            run.room = room
            fixture.roomObjects.enterRoom(run)
            val exits = exits(fixture, run)
            assertEquals(1, exits.size, run.roomrow.name)
            val entry = fixture.manager.coord(run, run.roomrow.arrival)
            val reachable = reachable(fixture, entry, 12)
            assertTrue(perimeter(exits.single().coords, exits.single().size).any { it in reachable },
                "Vyre Orator is unreachable in ${run.roomrow.name}")
            if (room != 5) assertFalse(run.roomrow.contains(fixture.manager.source(run, exits.single().coords)))
            assertTrue(reachable.flatMap { fixture.locationRepo.findAll(it) }
                .none { it.id == TheatreRoomObjects.EXIT.asRSCM(RSCMType.LOC) })
            previous?.let { old ->
                assertFalse(old.isSlotAssigned)
            }
            previous = exits.single()
        }
        fixture.roomObjects.clear(run)
        val finalExit = checkNotNull(previous)
        assertFalse(finalExit.isSlotAssigned)
    }

    @Test fun `dawnbringer pickups are private idempotent and isolated between five player parties`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val first = fixture.party(5, size = 5)
        val second = fixture.party(5, size = 5)
        fixture.roomObjects.enterRoom(first)
        fixture.roomObjects.enterRoom(second)
        val firstDrops = dawnbringers(fixture, first)
        val secondDrops = dawnbringers(fixture, second)
        assertEquals(5, firstDrops.size)
        assertEquals(5, secondDrops.size)
        for ((index, obj) in firstDrops.withIndex()) {
            val owner = first.members[index]
            assertTrue(obj.isPrivate)
            assertTrue(obj.isOriginalOwner(owner))
            assertTrue(obj.isVisibleTo(owner))
            assertTrue(first.members.filter { it !== owner }.none { obj.isVisibleTo(it) })
            assertTrue(second.members.none { obj.isVisibleTo(it) })
        }
        fixture.roomObjects.enterFinalRoom(first)
        assertEquals(firstDrops, dawnbringers(fixture, first))
        fixture.roomObjects.clear(first)
        assertTrue(dawnbringers(fixture, first).isEmpty())
        assertEquals(secondDrops, dawnbringers(fixture, second))
        fixture.roomObjects.clear(second)
    }

    @Test fun `cleared rooms keep their Orator while the party waits to advance`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(0, size = 2)
        fixture.encounters.start(run)
        fixture.roomObjects.enterRoom(run)
        fixture.manager.roomCleared(run)
        assertEquals(TheatreManager.CLEARED, run.stage)
        assertEquals(1, exits(fixture, run).size)
        assertNull(run.boss)
        assertTrue(run.members.all { it.vars[TheatreManager.DISCONNECT] == 0 })
        fixture.roomObjects.clear(run)
    }

    @Test fun `remaining player can advance after the captain leaves a cleared room without a fee`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(0, size = 2)
        val captain = run.members[0]
        val remaining = run.members[1]
        fixture.encounters.start(run)
        fixture.roomObjects.enterRoom(run)
        fixture.manager.roomCleared(run)
        fixture.manager.leave(captain)
        assertEquals(TheatreData.rules.exit, captain.coords)
        assertFalse(captain.uuid in run.session.occupants)
        assertTrue(remaining.uuid in run.session.occupants)
        assertEquals(1, exits(fixture, run).size)
        fixture.manager.advanceRoom(remaining)
        assertEquals(1, run.room)
        assertEquals(TheatreManager.WAITING, run.stage)
        assertNull(run.boss)
        assertEquals(fixture.manager.coord(run, run.roomrow.arrival), remaining.coords)
        assertEquals(1, exits(fixture, run).size)
        assertEquals(0, captain.vars[TheatreInventory.RECOVERY_FEE])
        assertEquals(0, remaining.vars[TheatreInventory.RECOVERY_FEE])
        fixture.roomObjects.clear(run)
    }

    @Test fun `phase transition removes ground and equipped dawnbringers while preserving the exit`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(5, size = 2)
        fixture.roomObjects.enterRoom(run)
        run.members[0].inv[0] = InvObj("obj.verzik_special_weapon")
        run.members[0].inv[1] = InvObj("obj.bones", 1, 77)
        run.members[1].worn[3] = InvObj("obj.verzik_special_weapon")
        run.members[1].appearance.javaClass.getDeclaredField("rebuild").apply { isAccessible = true }.setBoolean(run.members[1].appearance, false)
        fixture.manager.endDawnbringerPhase(run)
        assertTrue(dawnbringers(fixture, run).isEmpty())
        assertNull(run.members[0].inv[0])
        assertEquals(InvObj("obj.bones", 1, 77), run.members[0].inv[1])
        assertNull(run.members[1].worn[3])
        assertTrue(run.members[1].appearance.rebuild)
        assertEquals(1, exits(fixture, run).size)
        fixture.roomObjects.clear(run)
    }

    @Test fun `completion places native chests with per member ownership and separate party treasury`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val first = fixture.party(5, size = 2)
        val second = fixture.party(5)
        assertTrue(fixture.inventory.generateRewards(first.members[0]))
        assertTrue(fixture.inventory.generateRewards(first.members[1], unique = true))
        assertTrue(fixture.inventory.generateRewards(second.members[0]))
        fixture.roomObjects.enterRoom(first)
        first.stage = TheatreManager.COMPLETED
        second.stage = TheatreManager.COMPLETED
        fixture.roomObjects.complete(first)
        fixture.roomObjects.complete(second)
        for (run in listOf(first, second)) {
            val entry = fixture.manager.coord(run, TheatreData.rules.treasuryEntry)
            assertTrue(run.members.all { it.coords == entry })
            val exit = fixture.manager.coord(run, CoordGrid(3246, 4315, 0))
            val exitId = TheatreRoomObjects.EXIT.asRSCM(RSCMType.LOC)
            assertEquals(1, fixture.locationRepo.findAll(exit).count { it.id == exitId })
            val reachable = reachable(fixture, entry, 24)
            assertTrue(perimeter(exit, 1).any { it in reachable }, "Treasury exit is unreachable")
            for ((index, tile) in TheatreData.rules.rewardChests.withIndex()) {
                val actual = fixture.manager.coord(run, tile)
                val id = "loc.tob_treasureroom_chest_loc$index".asRSCM(RSCMType.LOC)
                assertEquals(1, fixture.locationRepo.findAll(actual).count { it.id == id })
                assertTrue(perimeter(actual, 3).any { it in reachable },
                    "Reward chest $index is unreachable")
            }
        }
        assertNotEquals(first.members[0].coords, second.members[0].coords)
        assertEquals(2, first.members[0].vars["varbit.tob_treasureroom_chest_0"])
        assertEquals(1, first.members[0].vars["varbit.tob_treasureroom_chest_1"])
        assertEquals(0, first.members[1].vars["varbit.tob_treasureroom_chest_0"])
        assertEquals(3, first.members[1].vars["varbit.tob_treasureroom_chest_1"])
        val secondChest = fixture.inventory.rewards(second.members.single()).toList()
        fixture.roomObjects.clear(first)
        assertEquals(secondChest, fixture.inventory.rewards(second.members.single()).toList())
        assertEquals(2, second.members[0].vars["varbit.tob_treasureroom_chest_0"])
        fixture.roomObjects.clear(second)
    }

    @Test fun `full and partial native chest claims keep ownership and remaining loot without a fee`() {
        val fixture = TheatreEncounterIntegrationTest.Fixture()
        val run = fixture.party(5)
        val player = run.members.single()
        val chest = fixture.inventory.rewards(player)
        chest[0] = InvObj("obj.bronze_dagger", 2, 77)
        chest[1] = InvObj("obj.coins", 150)
        VarPlayerIntMapSetter.set(player, TheatreInventory.CLAIM_READY, 1)
        run.stage = TheatreManager.COMPLETED
        fixture.roomObjects.complete(run)
        for (slot in player.inv.indices) player.inv[slot] = InvObj("obj.bones")
        player.inv[0] = InvObj("obj.coins", 100_000)
        val before = player.inv.toList()
        val rewardBefore = chest.toList()
        fixture.manager.claimRewards(player)
        assertEquals(100_150, player.inv[0]?.count)
        assertEquals(before.drop(1), player.inv.toList().drop(1))
        assertEquals(rewardBefore[0], chest[0])
        assertEquals(2, player.vars["varbit.tob_treasureroom_chest_0"])
        assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
        player.inv[1] = null
        fixture.manager.claimRewards(player)
        assertEquals(InvObj("obj.bronze_dagger", 1, 77), player.inv[1])
        assertEquals(InvObj("obj.bronze_dagger", 1, 77), chest[0])
        assertEquals(1, player.vars[TheatreInventory.CLAIM_READY])
        assertEquals(2, player.vars["varbit.tob_treasureroom_chest_0"])
        fixture.manager.claimRewards(player)
        assertEquals(InvObj("obj.bronze_dagger", 1, 77), chest[0])
        player.inv[2] = null
        fixture.manager.claimRewards(player)
        assertTrue(chest.isEmpty())
        assertEquals(0, player.vars[TheatreInventory.CLAIM_READY])
        assertEquals(0, player.vars["varbit.tob_treasureroom_chest_0"])
        assertEquals(100_150, player.inv[0]?.count)
        val collected = player.inv.toList()
        fixture.manager.claimRewards(player)
        assertEquals(collected, player.inv.toList())
        fixture.roomObjects.clear(run)
    }

    private fun exits(fixture: TheatreEncounterIntegrationTest.Fixture, run: TheatreRun): List<Npc> =
        fixture.npcs.filter { it.isType(TheatreRoomObjects.ORATOR) &&
            it.coords.chebyshevDistance(fixture.manager.coord(run, run.roomrow.arrival)) < 12 }

    private fun dawnbringers(fixture: TheatreEncounterIntegrationTest.Fixture, run: TheatreRun): List<Obj> {
        val id = "obj.verzik_special_weapon".asRSCM(RSCMType.OBJ)
        return TheatreData.rules.dawnbringerTiles.asSequence()
            .map { fixture.manager.coord(run, it) }
            .flatMap { fixture.objectRepo.findAll(it) }
            .filter { it.type == id }.toList()
    }

    private fun reachable(
        fixture: TheatreEncounterIntegrationTest.Fixture,
        entry: CoordGrid,
        radius: Int,
    ): Set<CoordGrid> {
        val steps = StepValidator(fixture.flags)
        val visited = mutableSetOf(entry)
        val pending = ArrayDeque<CoordGrid>().apply { add(entry) }
        val offsets = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (pending.isNotEmpty()) {
            val from = pending.removeFirst()
            for ((dx, dz) in offsets) {
                val to = from.translate(dx, dz)
                if (to in visited || entry.chebyshevDistance(to) > radius) continue
                if (steps.canTravel(from.level, from.x, from.z, dx, dz)) {
                    visited += to
                    pending += to
                }
            }
        }
        return visited
    }

    private fun perimeter(anchor: CoordGrid, size: Int): List<CoordGrid> = buildList {
        for (offset in 0 until size) {
            add(anchor.translate(-1, offset))
            add(anchor.translate(size, offset))
            add(anchor.translate(offset, -1))
            add(anchor.translate(offset, size))
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
