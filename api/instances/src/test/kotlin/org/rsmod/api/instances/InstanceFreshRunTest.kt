package org.rsmod.api.instances

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.instances.region.BaseAreaKey
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.api.instances.region.buildRegionTemplate
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class InstanceFreshRunTest {
    @Test
    fun `an owner returning to an empty encounter runs its opted in prelude`() {
        val script = TestScript(freshPrelude = true)
        val owner = Player().apply { uuid = 1L }
        val session = session(requireNotNull(owner.uuid))
        val joined = InstanceManager.Result.Joined(session, CoordGrid.ZERO)

        assertTrue(script.fresh(joined))
        assertTrue(script.prelude(owner, joined))
        session.addOccupant(requireNotNull(owner.uuid))
        assertFalse(script.fresh(joined))
        assertFalse(script.prelude(owner, joined))
        session.removeOccupant(requireNotNull(owner.uuid), currentTick = 100)
        assertTrue(script.fresh(joined))
        assertTrue(script.prelude(owner, joined))
    }

    @Test
    fun `legacy owner joins retain their behavior while guests still run the prelude`() {
        val script = TestScript(freshPrelude = false)
        val owner = Player().apply { uuid = 1L }
        val guest = Player().apply { uuid = 2L }
        val session = session(requireNotNull(owner.uuid))
        val joined = InstanceManager.Result.Joined(session, CoordGrid.ZERO)
        assertFalse(script.prelude(owner, joined))
        session.addOccupant(requireNotNull(owner.uuid))
        assertTrue(script.prelude(guest, joined))
        assertFalse(script.fresh(joined))
    }

    @Test
    fun `creation is fresh and a failed entry never runs a prelude`() {
        val script = TestScript(freshPrelude = true)
        val owner = Player().apply { uuid = 1L }
        val created = InstanceManager.Result.Created(session(requireNotNull(owner.uuid)), CoordGrid.ZERO)
        assertTrue(script.fresh(created))
        assertTrue(script.prelude(owner, created))
        val failed = InstanceManager.Result.Failed("No instance available")
        assertFalse(script.fresh(failed))
        assertFalse(script.prelude(owner, failed))
    }

    private class TestScript(private val freshPrelude: Boolean) : InstanceScript(BossInstanceRegistry()) {
        override fun area(): InstanceArea = InstanceArea.copyRegions(12588)
        override fun settingsRow(): String = "unused"
        override fun runsPreludeOnFreshRun(): Boolean = freshPrelude
        fun fresh(result: InstanceManager.Result): Boolean = result.isFreshRun()
        fun prelude(player: Player, result: InstanceManager.Result): Boolean {
            val method = InstanceScript::class.java.getDeclaredMethod(
                "runsEnterPrelude", Player::class.java, InstanceManager.Result::class.java,
            ).apply { isAccessible = true }
            return method.invoke(this, player, result) as Boolean
        }
    }

    private fun session(owner: Long): InstanceSession {
        val area = InstanceArea.copyRegions(12588)
        return InstanceSession(
            id = InstanceId(1),
            regionIds = mutableSetOf(),
            owner = owner,
            key = "test_boss",
            spec = InstanceSettings(maxPlayers = 2).withArea(area, settingsRowId = 0),
            placement = InstancePlacement(
                enterCoord = RegionLocal(0, 0, 0, 0, 0),
                exitCoord = CoordGrid.ZERO,
                regionTemplate = buildRegionTemplate(area.regionIds, area.level, area.rotation),
                npcSpawns = emptyList(),
                instanceLevel = 0,
                baseAreaKey = BaseAreaKey(0, 0, 0),
            ),
            access = InstanceAccess.Private,
        )
    }
}
