package org.rsmod.content.minigames.blastfurnace

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceMachine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class BlastFurnaceMachineryTest {
    @Test
    fun `manual machinery stays until the automatic workers are loaded`() {
        val f = Fixture()
        val pump = f.loc("loc.blast_furnace_pump", CoordGrid(1950, 4961))
        val pedals = f.loc("loc.blast_furnace_pedals", CoordGrid(1947, 4966))
        val belt = f.loc("loc.blast_furnace_conveyer_belt", CoordGrid(1942, 4966))
        f.machine.restoreMachinery()
        assertTrue(f.exists(pump))
        assertTrue(f.exists(pedals))

        val pumper = f.worker("pumper", pump.coords)
        val pedaller = f.worker("pedaller", pedals.coords)
        val slots = listOf(pumper.slotId, pedaller.slotId)
        f.machine.restoreMachinery()
        assertFalse(f.exists(pump))
        assertFalse(f.exists(pedals))
        assertTrue(f.exists(belt))
        assertEquals(slots, listOf(pumper.slotId, pedaller.slotId))
        assertTrue(pumper.isSlotAssigned && pedaller.isSlotAssigned)
        assertEquals(NpcMode.None, pumper.mode)
        assertEquals(NpcMode.None, pedaller.mode)

        repeat(120) {
            f.clock.tick()
            f.machine.restoreMachinery()
            f.machine.tick()
        }
        assertFalse(f.exists(pump))
        assertFalse(f.exists(pedals))
        assertTrue(f.exists(belt))
        val pendingRespawns = LocRepository::class.java.getDeclaredField("delDurations")
            .apply { isAccessible = true }.get(f.locs) as Collection<*>
        assertTrue(pendingRespawns.isEmpty())
    }

    @Test
    fun `worker loading before the room does not remove machinery prematurely`() {
        val f = Fixture()
        val pump = f.loc("loc.blast_furnace_pump", CoordGrid(1950, 4961))
        f.worker("pumper", pump.coords)
        f.machine.restoreMachinery()
        assertTrue(f.exists(pump))
        f.loc("loc.blast_furnace_conveyer_belt", CoordGrid(1942, 4966))
        f.machine.restoreMachinery()
        assertFalse(f.exists(pump))
    }

    @Test
    fun `removing worker mesh duplicates preserves objects outside the furnace`() {
        val f = Fixture()
        f.loc("loc.blast_furnace_conveyer_belt", CoordGrid(1942, 4966))
        f.worker("pumper", CoordGrid(1950, 4961))
        val elsewhere = f.loc("loc.blast_furnace_pump", CoordGrid(3200, 3200))
        f.machine.restoreMachinery()
        assertTrue(f.exists(elsewhere))
    }

    private class Fixture {
        val clock = MapClock(100)
        private val events = EventBus()
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val collision = CollisionFlagMap()
        private val npcList = NpcList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        private val npcs = NpcRepository(clock, npcRegistry, npcList)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal,
            collision, storage, npcRegistry, ControllerRegistry(clock, ControllerList()),
            ZonePlayerActivityBitSet())
        val locs = LocRepository(clock, LocRegistry(storage, normal,
            LocRegistryRegion(updates, collision, storage, regions)), regions)
        val machine = BlastFurnaceMachine(locs, npcs, WorldRepository(updates), collision)

        fun loc(symbol: String, coords: CoordGrid): LocInfo {
            allocate(coords)
            val loc = LocInfo(2, coords, LocEntity(symbol.asRSCM(), 10, 0))
            storage.mapLocs[ZoneKey.from(coords), LocZoneKey(ZoneGrid.from(coords), loc.layer)] = loc.entity
            return loc
        }

        fun worker(name: String, coords: CoordGrid): Npc {
            allocate(coords)
            val npc = Npc("npc.blast_furnace_automaton_$name", coords)
            npcs.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun exists(loc: LocInfo) = locs.findAll(loc.coords).any { it.id == loc.id }

        private fun allocate(coords: CoordGrid) {
            for (x in coords.x - 8..coords.x + 8) for (z in coords.z - 8..coords.z + 8) {
                collision.allocateIfAbsent(x, z, coords.level)
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() = BlastFurnaceCacheContractTest.loadCache()
    }
}
