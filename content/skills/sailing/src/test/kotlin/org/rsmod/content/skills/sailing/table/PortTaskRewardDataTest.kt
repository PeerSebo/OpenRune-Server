package org.rsmod.content.skills.sailing.table

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbHelper
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.PortTaskRow

@ResourceLock("ServerCacheManager")
class PortTaskRewardDataTest {
    @Test
    fun `packed anchors bounty rewards and exact courier payouts match the frozen catalogue`() {
        val baseline = baseline()
        val packed = PortTaskRewardData.packed
        assertEquals(29, baseline.anchors.size)
        assertEquals(21, baseline.bounties.size)
        assertEquals(1, baseline.couriers.size)
        assertEquals(baseline.anchors, packed.anchors)
        assertEquals(baseline.bounties, packed.bounties)
        assertEquals(baseline.couriers, packed.couriers)
        PortTaskRewards.assertCacheAnchors()
    }

    @Test
    fun `every native port task keeps its pre-migration XP and coin payout`() {
        val baseline = baseline()
        val intro = PortTaskRow.getRow("dbrow.port_task_sailing_intro")
        val tasks = PortTaskRow.all()
        assertTrue(tasks.size > 500)
        for (task in tasks) {
            val expected = when {
                task.taskType == 1 -> baseline.bounties[task.bountyTargetAlive?.id] ?: 0
                task.taskId == intro.taskId -> 100
                else -> previousCourierXp(task, baseline)
            }
            assertEquals(expected, PortTaskRewards.xpFor(task), "XP for task ${task.rowId}")
            val coins = if (task.taskType == 1 || task.rowId == intro.rowId) 0 else expected
            assertEquals(coins, PortTaskRewards.coinsFor(task), "Coins for task ${task.rowId}")
        }
    }

    @Test
    fun `missing and duplicate catalogues fail validation instead of silently dropping data`() {
        val anchors = DbHelper.table("dbtable.sailing_port_reward_anchors")
        val bounties = DbHelper.table("dbtable.sailing_bounty_rewards")
        val couriers = DbHelper.table("dbtable.sailing_courier_rewards")
        assertThrows(IllegalArgumentException::class.java) { PortTaskRewardData.read(emptyList(), bounties, couriers) }
        assertThrows(IllegalArgumentException::class.java) { PortTaskRewardData.read(anchors, emptyList(), couriers) }
        assertThrows(IllegalArgumentException::class.java) { PortTaskRewardData.read(anchors, bounties, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { PortTaskRewardData.read(anchors + anchors.first(), bounties, couriers) }
        assertThrows(IllegalArgumentException::class.java) { PortTaskRewardData.read(anchors, bounties + bounties.first(), couriers) }
        assertThrows(IllegalArgumentException::class.java) { PortTaskRewardData.read(anchors, bounties, couriers + couriers.first()) }
    }

    private fun previousCourierXp(task: PortTaskRow, baseline: PortTaskRewardData): Int {
        val end = task.endingPort.rowId
        val cargo = (task.cargoPort ?: task.startingPort ?: task.endingPort).rowId
        val start = task.startingPort?.rowId ?: cargo
        val route = listOf(start, cargo, end).zipWithNext().filter { it.first != it.second }
        if (route.isEmpty()) return 10
        if (route.any { it.first !in baseline.anchors || it.second !in baseline.anchors }) {
            val fallback = (78 * task.levelRequired.coerceAtLeast(1).toDouble().pow(1.11)).roundToInt().coerceAtLeast(10)
            return (fallback * route.size).coerceIn(10, 11519)
        }
        return route.sumOf { (from, to) ->
            val a = baseline.anchors.getValue(from)
            val b = baseline.anchors.getValue(to)
            val distance = hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())
            (78 * (distance / 232.0).pow(3.136)).roundToInt().coerceAtLeast(0)
        }.coerceIn(10, 11519)
    }

    private fun baseline(): PortTaskRewardData {
        val anchors = mutableMapOf<Int, PortTaskRewards.PortAnchor>()
        val bounties = mutableMapOf<Int, Int>()
        val couriers = mutableMapOf<Int, PortTaskRewardData.CourierReward>()
        val lines = requireNotNull(javaClass.getResourceAsStream("/port-task-rewards-baseline.csv"))
            .bufferedReader().use { it.readLines() }
        for (line in lines) {
            val cells = line.split(',')
            val id = cells[1].asRSCM()
            when (cells[0]) {
                "anchor" -> anchors[id] = PortTaskRewards.PortAnchor(cells[2].toInt(), cells[3].toInt(), cells[4].toBooleanStrict())
                "bounty" -> bounties[id] = cells[2].toInt()
                "courier" -> couriers[id] = PortTaskRewardData.CourierReward(cells[2].toInt(), cells[3].toInt())
                else -> error("Unknown frozen reward kind: $line")
            }
        }
        return PortTaskRewardData(anchors, bounties, couriers)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
