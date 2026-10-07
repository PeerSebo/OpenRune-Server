package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DesertTreasure2DropsTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `pending vestige rolls do not invent visible interim loot`() {
        val player = Player()
        val roll = vestigeProgressRoll(PROGRESS, "obj.magus_vestige")
        assertInstanceOf(RollResult.Nothing::class.java, roll.selectResult(player, ArgMap.Empty))
        assertEquals(1, player.vars[PROGRESS])
        assertInstanceOf(RollResult.Nothing::class.java, roll.selectResult(player, ArgMap.Empty))
        assertEquals(2, player.vars[PROGRESS])
        val success = assertInstanceOf(RollResult.Single::class.java, roll.selectResult(player, ArgMap.Empty))
        assertEquals(org.rsmod.api.droptable.DropRollItem("obj.magus_vestige", 1), success.result)
        assertEquals(0, player.vars[PROGRESS])
    }

    @Test fun `vestige progress survives player state restoration`() {
        val first = Player()
        val roll = vestigeProgressRoll(PROGRESS, "obj.magus_vestige")
        roll.selectResult(first, ArgMap.Empty)
        roll.selectResult(first, ArgMap.Empty)
        val restored = Player().apply { vars.backing.putAll(first.vars.backing) }
        assertInstanceOf(RollResult.Single::class.java, roll.selectResult(restored, ArgMap.Empty))
        assertEquals(0, restored.vars[PROGRESS])
    }

    @Test fun `normal boss defeats cannot grant a sanguine kit`() {
        val player = Player()
        val npc = Npc("npc.duke_sucellus_asleep", CoordGrid(3036, 6452))
        assertFalse(player.shouldDropSanguineTorvaKit(npc, "obj.dt2_sanguine_torva_kit"))
    }

    private companion object { const val PROGRESS = "varp.duke_sucellus_vestige_progress" }
}
