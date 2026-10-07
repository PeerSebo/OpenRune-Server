package org.rsmod.api.instances

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.api.instances.region.BaseAreaKey
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.api.instances.region.buildRegionTemplate
import org.rsmod.api.instances.region.regionIdsGrid
import org.rsmod.map.CoordGrid

class ServerHostedInstanceTest {

    @Test
    fun `region id grid spans the requested block of map squares`() {
        assertEquals(listOf(12588), regionIdsGrid(12588, gridSize = 1))

        val grid3 = regionIdsGrid(12588, gridSize = 3)
        assertEquals(9, grid3.size)
        assertEquals(9, grid3.toSet().size)
        assertTrue(12588 in grid3)
        assertTrue(((49 - 1) shl 8 or (44 - 1)) in grid3)
        assertTrue(((49 + 1) shl 8 or (44 + 1)) in grid3)

        assertEquals(25, regionIdsGrid(12588, gridSize = 5).size)
    }

    @Test
    fun `grid size must be a positive odd number`() {
        assertThrows<IllegalArgumentException> { regionIdsGrid(12588, gridSize = 2) }
        assertThrows<IllegalArgumentException> { regionIdsGrid(12588, gridSize = 0) }
    }

    @Test
    fun `multi-region areas build a region template up to the large-region ceiling`() {
        for (gridSize in intArrayOf(1, 3, 5)) {
            val area = InstanceArea.copyRegions(centerRegionId = 12588, gridSize = gridSize)
            assertEquals(gridSize * gridSize, area.regionIds.size)
            buildRegionTemplate(area.regionIds, area.level, area.rotation)
        }
    }

    @Test
    fun `a region block wider than the large-region build square is rejected up front`() {
        val area = InstanceArea.copyRegions(centerRegionId = 12588, gridSize = 7)
        val error =
            assertThrows<IllegalArgumentException> {
                buildRegionTemplate(area.regionIds, area.level, area.rotation)
            }
        assertTrue(error.message.orEmpty().contains("too large to copy"), error.message)
    }

    @Test
    fun `a rotated multi-region area is measured on its rotated footprint`() {
        val strip = (49..53).map { mapSquareX -> (mapSquareX shl 8) or 44 }
        buildRegionTemplate(strip, level = 0, rotation = InstanceRegionRotation.CLOCKWISE_90)
    }

    @Test
    fun `unrelated players join a server hosted session and leaving does not end it`() {
        val session = serverHostedSession(maxPlayers = 3)

        assertTrue(session.acceptsNewPlayers())

        session.addOccupant(ALICE)
        session.addOccupant(BOB)
        session.addOccupant(CARLA)
        assertTrue(session.isFull())
        assertFalse(session.acceptsNewPlayers())

        session.removeOccupant(BOB, currentTick = 500)
        assertEquals(setOf(ALICE, CARLA), session.occupants)
        assertEquals(SessionState.Active, session.state)
        assertTrue(session.acceptsNewPlayers())

        session.removeOccupant(ALICE, currentTick = 501)
        session.removeOccupant(CARLA, currentTick = 501)
        assertTrue(session.occupants.isEmpty())
        assertEquals(SessionState.Active, session.state)
        assertFalse(session.isExpired(currentTick = 999_999))
    }

    @Test
    fun `closing the lobby stops new joins without disturbing the occupants`() {
        val session = serverHostedSession(maxPlayers = 20)
        session.addOccupant(ALICE)

        session.acceptingPlayers = false
        assertFalse(session.acceptsNewPlayers())
        assertEquals(setOf(ALICE), session.occupants)

        session.resetState()
        assertTrue(session.acceptingPlayers)
        assertTrue(session.acceptsNewPlayers())
    }

    @Test
    fun `an owner bound session still enters reclaim when it empties`() {
        val session = ownerBoundSession()
        session.addOccupant(ALICE)
        session.removeOccupant(ALICE, currentTick = 100)

        val state = assertIs<SessionState.Reclaim>(session.state)
        assertEquals(100 + session.spec.reclaimTicks, state.deadlineTick)
    }

    private fun serverHostedSession(maxPlayers: Int): InstanceSession =
        session(maxPlayers = maxPlayers, owner = 0L, serverOwned = true, access = InstanceAccess.Public)

    private fun ownerBoundSession(): InstanceSession =
        session(maxPlayers = 1, owner = ALICE, serverOwned = false, access = InstanceAccess.Private)

    private fun session(
        maxPlayers: Int,
        owner: Long,
        serverOwned: Boolean,
        access: InstanceAccess,
    ): InstanceSession {
        val area = InstanceArea.copyRegions(centerRegionId = 12588, gridSize = 3)
        val spec = InstanceSettings(maxPlayers = maxPlayers).withArea(area, settingsRowId = 0)
        val placement =
            InstancePlacement(
                enterCoord = RegionLocal(0, 0, 0, 0, 0),
                exitCoord = CoordGrid.ZERO,
                regionTemplate = buildRegionTemplate(area.regionIds, area.level, area.rotation),
                npcSpawns = emptyList(),
                instanceLevel = 0,
                baseAreaKey = BaseAreaKey(0, 0, 0),
            )
        return InstanceSession(
            id = InstanceId(1),
            regionIds = mutableSetOf(),
            owner = owner,
            key = "test_activity",
            spec = spec,
            placement = placement,
            access = access,
            isServerOwned = serverOwned,
        )
    }

    private companion object {
        private const val ALICE = 1L
        private const val BOB = 2L
        private const val CARLA = 3L
    }
}
