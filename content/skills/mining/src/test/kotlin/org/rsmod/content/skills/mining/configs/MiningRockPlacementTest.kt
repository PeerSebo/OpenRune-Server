package org.rsmod.content.skills.mining.configs

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.BoundLocInfo
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

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MiningRockPlacementTest {
    @ParameterizedTest(name = "shape {0}, rotation {1}")
    @CsvSource("10,0", "10,1", "10,2", "10,3", "11,0", "11,1", "11,2", "11,3")
    fun `depletion and timed respawn preserve rock placement on the map and wire`(
        shape: Int,
        rotation: Int,
    ) {
        assertNull(ServerCacheManager.getObject(FULL_ID))
        assertNull(ServerCacheManager.getObject(EMPTY_ID))
        val fixture = Fixture()
        val coords = CoordGrid(3055, 9781)
        val full = LocInfo(2, coords, LocEntity(FULL_ID, shape, rotation))
        val zone = ZoneKey.from(coords)
        fixture.storage.mapLocs[zone, LocZoneKey(ZoneGrid.from(coords), full.layer)] = full.entity
        assertEquals(full, fixture.repository.findAll(coords).single())

        val bound = BoundLocInfo(full, 1, 1, 0)
        fixture.repository.change(bound, ObjectServerType(id = EMPTY_ID), duration = 4)

        val empty = full.copy(entity = full.entity.copy(id = EMPTY_ID))
        assertEquals(empty, fixture.repository.findAll(coords).single())
        assertEquals(1, fixture.storage.spawnedLocCount())
        assertPacket(fixture, empty)
        fixture.updates.clear()

        fixture.clock.cycle += 3
        fixture.processDurations()
        assertEquals(empty, fixture.repository.findAll(coords).single())
        assertTrue(fixture.updates.updatedZones.isEmpty())

        fixture.clock.tick()
        fixture.processDurations()
        assertEquals(full, fixture.repository.findAll(coords).single())
        assertEquals(0, fixture.storage.spawnedLocCount())
        assertPacket(fixture, full)
        fixture.updates.clear()

        fixture.clock.tick()
        fixture.processDurations()
        assertEquals(full, fixture.repository.findAll(coords).single())
        assertTrue(fixture.updates.updatedZones.isEmpty())
    }

    private fun assertPacket(fixture: Fixture, expected: LocInfo) {
        assertEquals(1, fixture.updates.updatedZones.size)
        val zone = ZoneKey.from(expected.coords)
        val packets = checkNotNull(fixture.updates.updatedZones[zone.packed])
        val packet = packets.single() as LocAddChangeV2
        val grid = ZoneGrid.from(expected.coords)
        assertEquals(expected.id, packet.id)
        assertEquals(expected.shapeId, packet.shape)
        assertEquals(expected.angleId, packet.rotation)
        assertEquals(grid.x, packet.xInZone)
        assertEquals(grid.z, packet.zInZone)
    }

    private class Fixture {
        val clock = MapClock(100)
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        private val collision = CollisionFlagMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(
            RegionListSmall(),
            RegionListLarge(),
            normal,
            collision,
            storage,
            NpcRegistry(NpcList(), collision, EventBus()),
            ControllerRegistry(clock, ControllerList()),
            ZonePlayerActivityBitSet(),
        )
        private val registry = LocRegistry(
            storage,
            normal,
            LocRegistryRegion(updates, collision, storage, regions),
        )
        val repository = LocRepository(clock, registry, regions)

        fun processDurations() {
            // Invoke the real internal timer processor without constructing unrelated repositories.
            durationProcessor.invoke(repository)
        }
    }

    private companion object {
        const val FULL_ID = 65530
        const val EMPTY_ID = 65531
        val durationProcessor = LocRepository::class.java.declaredMethods.single {
            it.name.startsWith("processDurations") && it.parameterCount == 0
        }.apply { isAccessible = true }
    }
}
