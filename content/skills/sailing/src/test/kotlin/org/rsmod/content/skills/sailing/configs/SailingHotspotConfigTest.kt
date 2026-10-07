package org.rsmod.content.skills.sailing.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import org.rsmod.content.skills.sailing.BoatHullEntity
import org.rsmod.content.skills.sailing.BoatOwnershipService
import org.rsmod.content.skills.sailing.FacilityService
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.SailingCargoHold
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.routefinder.collision.CollisionFlagMap

// Native scripts retrieve hotspot layouts by list ordinal, not tuple rotation.
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingHotspotConfigTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @ParameterizedTest
    @CsvSource("0,1", "1,7", "2,13")
    fun `every native hotspot has one distinct ordinal and matching layout`(typeId: Int, count: Int) {
        val boat = checkNotNull(SailingBoats.byTypeId(typeId))
        val hotspots = SailingBoats.hotspots(boat)
        assertEquals(count, boat.facilityAmount)
        assertEquals((0 until count).toList(), hotspots.map { it.hotspotIndex })
        val facilities = Fixture().facilities
        for (index in 0 until count) {
            val hotspot = hotspots[index]
            assertEquals(boat.hotspot[index].t3.id, hotspot.layoutRowId)
            assertEquals(hotspot.layoutRowId, facilities.layout(boat, index)?.rowId)
            val placeholder = checkNotNull(facilities.placeholderLoc(boat, index))
            assertNotNull(ServerCacheManager.getObject(placeholder.asRSCM()))
            assertEquals(index, facilities.placeholderKey(placeholder.asRSCM())?.hotspotIndex)
        }
    }

    @Test
    fun `reshaping retains native coordinates rotation shape field and layout within each deck`() {
        for (hull in BoatHullEntity.entries) {
            val boat = checkNotNull(SailingBoats.byTypeId(hull.typeId))
            val deck = hull.deckSource
            for ((index, hotspot) in SailingBoats.hotspots(boat).withIndex()) {
                val native = boat.hotspot[index]
                assertEquals(native.t0, hotspot.coords)
                assertEquals(native.t1, hotspot.angle)
                assertEquals(LocShape[native.t2], hotspot.shape)
                assertEquals(native.t3.id, hotspot.layoutRowId)
                assertTrue(hotspot.angle in 0..3)
                assertEquals(deck.southWestZone.level, hotspot.coords.level)
                assertTrue(hotspot.coords.x in deck.southWestZone.x * 8 until (deck.southWestZone.x + deck.zoneSizeX) * 8)
                assertTrue(hotspot.coords.z in deck.southWestZone.z * 8 until (deck.southWestZone.z + deck.zoneSizeZ) * 8)
            }
        }
    }

    @Test
    fun `facility state round trips independently through every skiff and sloop hotspot`() {
        val facilities = Fixture().facilities
        for (boat in listOf(SailingBoats.skiff, SailingBoats.sloop)) {
            val player = Player()
            val expected = boat.hotspot.indices.associateWith { index ->
                val option = facilities.options(boat, index).first { facilities.facilityIdOf(it) != null }
                assertTrue(facilities.setBuiltFacility(player, 1, index, option))
                option.rowId
            }
            assertEquals(expected, facilities.builtFacilities(player, 1, boat).mapValues { it.value.rowId })
            val last = boat.hotspot.lastIndex
            assertTrue(facilities.setBuiltFacility(player, 1, last, null))
            assertEquals(expected - last, facilities.builtFacilities(player, 1, boat).mapValues { it.value.rowId })
        }
    }

    @Test
    fun `cargo capacity resolves a hold on its own ordinal instead of another matching rotation`() {
        val facilities = Fixture().facilities
        for (boat in SailingBoats.all) {
            var checked = 0
            for (index in boat.hotspot.indices) {
                val hold = facilities.options(boat, index).firstOrNull {
                    it.facilityStats?.boatCargoholdSize != null && facilities.facilityIdOf(it) != null
                } ?: continue
                val player = Player().apply {
                    writeSailingVar(SailingBoatVars.owned(1), 1)
                    writeSailingVar(SailingBoatVars.field(1, "type"), boat.typeId)
                }
                assertTrue(facilities.setBuiltFacility(player, 1, index, hold))
                assertEquals(checkNotNull(hold.facilityStats).boatCargoholdSize, SailingCargoHold.capacity(player, 1))
                checked++
            }
            assertTrue(checked > 0, "No cargo hold layout exercised for ${boat.name}")
        }
    }

    @Test
    fun `static facilities retain their separate native tuple order`() {
        for (boat in SailingBoats.all) {
            for ((index, facility) in SailingBoats.staticFacilities(boat).withIndex()) {
                val native = boat.staticFacility[index]
                assertEquals(native.t0, facility.coords)
                assertEquals(native.t1.id, facility.loc.id)
                assertEquals(native.t2, facility.angle)
                assertEquals(LocShape[native.t3], facility.shape)
            }
        }
    }

    private class Fixture {
        private val clock = MapClock(100)
        private val collision = CollisionFlagMap()
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, EventBus()),
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
        )
        private val locs = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        val facilities = FacilityService(LocRepository(clock, locs, regions), BoatOwnershipService())
    }
}
