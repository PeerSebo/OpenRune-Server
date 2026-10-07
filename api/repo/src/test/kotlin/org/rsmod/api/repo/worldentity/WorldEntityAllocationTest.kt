package org.rsmod.api.repo.worldentity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class WorldEntityAllocationTest {
    @Test
    fun `boat decks use their own band and survive empty-instance sweeps`() {
        val fixture = Fixture()
        val boat = fixture.add()
        assertEquals(CoordGrid(14080, 0), boat.instanceSouthWest)
        assertEquals(64, boat.region.northEast.x - boat.region.southWest.x)
        fixture.registry.removeInactiveSmallRegions()
        fixture.registry.removeInactiveLargeRegions()
        assertTrue(fixture.regions.isValid(boat.region))
        assertSame(boat.region, fixture.registry[boat.instanceSouthWest])
    }

    @Test
    fun `building a neighboring deck preserves the first deck edge collision`() {
        val fixture = Fixture()
        fixture.collision.allocateIfAbsent(3200, 3200, 0)
        fixture.collision.add(3200, 3200, 0, CollisionFlag.BLOCK_WALK)
        val first = fixture.add(RegionTemplate.create { this[7, 0, 0] = ZoneKey(400, 400, 0) })
        val edge = first.instanceSouthWest.translate(56, 0)
        assertTrue(fixture.collision.isZoneValid(ZoneKey.from(edge)))
        val second = fixture.add()
        assertEquals(first.instanceSouthWest.translate(64, 0), second.instanceSouthWest)
        assertTrue(fixture.collision.isZoneValid(ZoneKey.from(edge)))
        assertTrue(fixture.collision[edge.x, edge.z, edge.level] and CollisionFlag.BLOCK_WALK != 0)
        assertTrue(fixture.boats.del(second))
        assertTrue(fixture.collision.isZoneValid(ZoneKey.from(edge)))
    }

    @Test
    fun `avatar allocation failure releases its deck without disturbing other boats`() {
        val fixture = Fixture()
        val first = fixture.add()
        fixture.events.subscribeUnbound(WorldEntityStateEvents.Create::class.java) {
            error("avatar allocation failed")
        }
        assertNull(fixture.tryAdd())
        assertTrue(fixture.regions.isValid(first.region))
        assertNull(fixture.registry[first.instanceSouthWest.translate(64, 0)])
        assertEquals(1, fixture.entities.count())
    }

    @Test
    fun `avatar release failure retains the deck for retry`() {
        val fixture = Fixture()
        val boat = fixture.add()
        var fail = true
        fixture.events.subscribeUnbound(WorldEntityStateEvents.Delete::class.java) {
            if (fail) error("avatar release failed")
        }
        assertFalse(fixture.boats.del(boat))
        assertTrue(fixture.regions.isValid(boat.region))
        assertEquals(1, fixture.entities.count())
        fail = false
        assertTrue(fixture.boats.del(boat))
        assertFalse(fixture.regions.isValid(boat.region))
        assertNull(fixture.registry[boat.instanceSouthWest])
        assertEquals(0, fixture.entities.count())
        assertTrue(fixture.boats.del(boat))
    }

    @Test
    fun `invalid root and oversized deck rectangle release their allocations`() {
        val fixture = Fixture()
        assertNull(fixture.tryAdd(root = CoordGrid.NULL))
        assertNull(fixture.tryAdd(sizeX = 9))
        assertNull(fixture.registry[CoordGrid(14080, 0)])
        assertNull(fixture.registry[CoordGrid(14144, 0)])
        assertEquals(0, fixture.entities.count())
    }

    @Test
    fun `template outside the deck square is rejected before changing neighboring zones`() {
        val fixture = Fixture()
        val first = fixture.add()
        val tooWide = RegionTemplate.create { this[8, 0, 0] = ZoneKey(400, 400, 0) }
        assertThrows<IllegalArgumentException> { fixture.tryAdd(tooWide) }
        assertTrue(fixture.regions.isValid(first.region))
        assertNull(fixture.registry[first.instanceSouthWest.translate(64, 0)])
        assertEquals(1, fixture.entities.count())
    }

    private class Fixture {
        val events = EventBus()
        val collision = CollisionFlagMap()
        private val storage = LocZoneStorage()
        val registry = RegionRegistry(
            RegionListSmall(), RegionListLarge(),
            LocRegistryNormal(ZoneUpdateMap(), collision, storage), collision, storage,
            NpcRegistry(NpcList(), collision, events),
            ControllerRegistry(MapClock(), ControllerList()), ZonePlayerActivityBitSet(),
        )
        val regions = RegionRepository(registry)
        val entities = WorldEntityRegistry(WorldEntityList(), events)
        val boats = WorldEntityRepository(entities, regions)

        fun add(template: RegionTemplate = RegionTemplate.create {}): WorldEntity {
            val boat = tryAdd(template)
            assertNotNull(boat)
            return checkNotNull(boat)
        }

        fun tryAdd(
            template: RegionTemplate = RegionTemplate.create {},
            root: CoordGrid = CoordGrid(3051, 3193),
            sizeX: Int = 1,
        ): WorldEntity? = boats.add(1, template, sizeX, 1, root)
    }
}
