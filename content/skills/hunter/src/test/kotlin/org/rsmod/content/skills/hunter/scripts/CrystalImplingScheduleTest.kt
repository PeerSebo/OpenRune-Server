package org.rsmod.content.skills.hunter.scripts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CrystalImplingScheduleTest {
    @Test fun `spawns only once after the three minute gate and clears at the next half hour`() {
        val schedule = CrystalImplingSchedule()
        assertEquals(CrystalImplingSchedule.Change(true, false), schedule.update(120))
        assertEquals(CrystalImplingSchedule.Change(false, false), schedule.update(122))
        assertEquals(CrystalImplingSchedule.Change(false, true), schedule.update(123))
        assertEquals(CrystalImplingSchedule.Change(false, false), schedule.update(123))
        assertEquals(CrystalImplingSchedule.Change(false, false), schedule.update(149))
        assertEquals(CrystalImplingSchedule.Change(true, false), schedule.update(150))
        assertEquals(CrystalImplingSchedule.Change(false, true), schedule.update(153))
    }

    @Test fun `starting during an existing window spawns once but clock rollback cannot replay a window`() {
        val schedule = CrystalImplingSchedule()
        assertEquals(CrystalImplingSchedule.Change(true, true), schedule.update(137))
        assertEquals(CrystalImplingSchedule.Change(false, false), schedule.update(123))
        assertEquals(CrystalImplingSchedule.Change(false, false), schedule.update(93))
        assertEquals(CrystalImplingSchedule.Change(false, false), schedule.update(149))
        assertEquals(CrystalImplingSchedule.Change(true, true), schedule.update(167))
    }
}
