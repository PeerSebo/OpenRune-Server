package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.ui.IfCloseSub
import org.rsmod.api.player.ui.ifCloseModals
import org.rsmod.api.table.sailing.SailingBoatHotspotRow
import org.rsmod.content.skills.sailing.BoatHullEntity
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingSidepanelTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `base skiff uses actual boat row and zero based native part indexes`() {
        val fixture = SailingBoardingLifecycleTest.Fixture(hull = BoatHullEntity.SKIFF)

        SailingSidepanel.open(fixture.access(), 1)

        assertEquals(SailingBoats.skiff.rowId, fixture.player.readSailingVar(SailingSidepanel.VAR_BOAT_TYPE))
        assertNotEquals(SailingBoats.skiff.typeId, fixture.player.readSailingVar(SailingSidepanel.VAR_BOAT_TYPE))
        assertEquals(0, fixture.player.readSailingVar(SailingSidepanel.VAR_FACILITY_SAIL))
        assertEquals(0, fixture.player.readSailingVar(SailingSidepanel.VAR_FACILITY_HELM))
        assertEquals(0, fixture.player.readSailingVar(SailingSidepanel.VAR_FACILITY_HULL))
        assertEquals(10, fixture.player.readSailingVar(SailingSidepanel.VAR_PLAYER_ROLE))
        assertEquals(0, fixture.player.readSailingVar(SailingSidepanel.VAR_TABS))
    }

    @Test
    fun `upgraded fitted parts select their native cache list indexes`() {
        val fixture = SailingBoardingLifecycleTest.Fixture(hull = BoatHullEntity.SKIFF)
        val boat = SailingBoats.skiff
        fixture.player.writeSailingVar(SailingBoatVars.hull(1), 2)
        fixture.player.writeSailingVar(SailingBoatVars.sail(1), 3)
        fixture.player.writeSailingVar(SailingBoatVars.field(1, "steering"), 1)

        SailingSidepanel.refresh(fixture.access(), 1)

        val hull = boat.hullOption.sortedBy { it.sailingRequirement }[2]
        val sail = boat.sailOption.sortedBy { it.sailingRequirement }[3]
        val helm = boat.steeringOption.sortedBy { it.sailingRequirement }[1]
        assertEquals(boat.hullOption.indexOfFirst { it.rowId == hull.rowId }, fixture.player.readSailingVar(SailingSidepanel.VAR_FACILITY_HULL))
        assertEquals(boat.sailOption.indexOfFirst { it.rowId == sail.rowId }, fixture.player.readSailingVar(SailingSidepanel.VAR_FACILITY_SAIL))
        assertEquals(boat.steeringOption.indexOfFirst { it.rowId == helm.rowId }, fixture.player.readSailingVar(SailingSidepanel.VAR_FACILITY_HELM))
    }

    @Test
    fun `stored facility bottle id becomes native one based hotspot option`() {
        for (hull in BoatHullEntity.entries) {
            val fixture = SailingBoardingLifecycleTest.Fixture(hull = hull)
            val boat = checkNotNull(SailingBoats.byTypeId(hull.typeId))
            for (hotspot in SailingBoats.hotspots(boat)) {
                val options = SailingBoatHotspotRow.getRow(hotspot.layoutRowId).option
                val chosen = options.withIndex().first { (it.value.facilityBottleId ?: 0) > 0 }
                val storageVar = SailingBoatVars.hotspot(1, hotspot.hotspotIndex)
                val panelVar = "varbit.sailing_sidepanel_facility_hotspot${hotspot.hotspotIndex}"
                fixture.player.writeSailingVar(storageVar, checkNotNull(chosen.value.facilityBottleId))
                SailingSidepanel.refresh(fixture.access(), 1)
                assertEquals(chosen.index + 1, fixture.player.readSailingVar(panelVar))
                fixture.player.writeSailingVar(storageVar, 0)
                SailingSidepanel.refresh(fixture.access(), 1)
                assertEquals(0, fixture.player.readSailingVar(panelVar))
            }
        }
    }

    @Test
    fun `native combat overlay survives closing action modals`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        SailingSidepanel.open(fixture.access(), 1)

        fixture.player.ifCloseModals(fixture.eventBus)

        assertFalse(fixture.player.ui.containsModal(SailingSidepanel.INTERFACE))
        assertTrue(fixture.player.ui.containsOverlay(SailingSidepanel.INTERFACE))
        assertEquals(UserInterface(SailingSidepanel.INTERFACE.asRSCM()), fixture.player.ui.overlays[Component(SailingSidepanel.PARENT.asRSCM())])
        assertEquals(1, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE))
    }

    @Test
    fun `closing restores combat tab and clears both sailing visibility flags`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        SailingSidepanel.open(fixture.access(), 1)
        assertEquals(1, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE_FROM_COMBAT))

        SailingSidepanel.close(fixture.access())

        assertFalse(SailingSidepanel.INTERFACE in fixture.player.ui)
        assertEquals(UserInterface("interface.combat_interface".asRSCM()), fixture.player.ui.overlays[Component(SailingSidepanel.PARENT.asRSCM())])
        assertEquals(0, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE))
        assertEquals(0, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE_FROM_COMBAT))
    }

    @Test
    fun `reopening remains visible after prior panel close hook`() {
        val fixture = SailingBoardingLifecycleTest.Fixture()
        fixture.eventBus.subscribeKeyed(IfCloseSub::class.java, SailingSidepanel.INTERFACE.asRSCM().toLong()) {
            SailingSidepanel.onClosed(player)
        }
        SailingSidepanel.open(fixture.access(), 1)

        SailingSidepanel.open(fixture.access(), 1)

        assertEquals(1, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE))
        assertEquals(1, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE_FROM_COMBAT))
    }
}
