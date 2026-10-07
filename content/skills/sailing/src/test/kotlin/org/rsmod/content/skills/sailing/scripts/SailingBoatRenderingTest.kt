package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.sailing.BoatHullEntity
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.isOnPlayerBoat
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingBoatRenderingTest {
    @BeforeAll
    fun loadPackedCache() { ServerCacheManager.init(241).close() }

    @Test
    fun `raft boarding includes the real hull beneath the deck and an operable helm and cloth`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        val entity = fixture.boardDirectly()
        val base = entity.instanceSouthWest
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 2, 0), 59494))
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 3, 1), 59530))
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 4, 1), 59554))
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 5, 1), 29506))
        for (level in 0..3) {
            val copy = fixture.regions[ZoneKey(base.x / 8, base.z / 8, level)]
            assertEquals(ZoneKey(480, 807, level), copy.normalZone())
        }
    }

    @Test
    fun `every supported hull receives its actual helm and sails inside its own entity`() {
        for (hull in BoatHullEntity.entries) {
            val fixture = SailingBoardingLifecycleTest.Fixture(hull = hull)
            val entity = fixture.boardDirectly()
            val status = checkNotNull(SailingSidepanel.status(fixture.player, 1))
            val source = hull.deckSource.southWestZone
            for (entry in checkNotNull(status.steering).loc) {
                val coords = CoordGrid(entity.instanceSouthWest.x + entry.t0.x - source.x * 8,
                    entity.instanceSouthWest.z + entry.t0.z - source.z * 8, entry.t0.level)
                assertNotNull(fixture.locRegistry.findType(coords, entry.t1.id), "${hull.name} helm")
            }
        }
    }

    @Test
    fun `stored hull and mast upgrades are drawn when the boat is boarded`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        fixture.player.writeSailingVar(SailingBoatVars.field(1, "hull"), 1)
        fixture.player.writeSailingVar(SailingBoatVars.field(1, "sail"), 1)
        fixture.player.writeSailingVar(SailingBoatVars.field(1, "steering"), 1)
        val entity = fixture.boardDirectly()
        val base = entity.instanceSouthWest
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 2, 0), 59495))
        assertNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 2, 0), 59494))
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 3, 1), 59531))
        assertNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 4, 1), 59557))
    }

    @Test
    fun `missing source hull refuses boarding and releases its allocation`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        val source = checkNotNull(fixture.locs.findExact(CoordGrid(3843, 6458, 0), SailingBoats.baseHull(SailingBoats.raft).loc))
        fixture.locs.del(source, Int.MAX_VALUE)
        fixture.finishBoard()
        assertFalse(fixture.player.isOnPlayerBoat())
        assertNull(fixture.boats.boarded(fixture.player))
        assertEquals(0, fixture.registry.count())
        assertEquals(fixture.shore, fixture.player.coords)
    }
}
