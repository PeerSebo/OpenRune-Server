package org.rsmod.content.skills.sailing.table

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.DbRowIdCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.NpcTypeCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.rsmod.api.table.PortTaskRow

internal data class PortTaskRewardData(
    val anchors: Map<Int, PortTaskRewards.PortAnchor>,
    val bounties: Map<Int, Int>,
    val couriers: Map<Int, CourierReward>,
) {
    data class CourierReward(val xp: Int, val coins: Int)

    companion object {
        val packed: PortTaskRewardData by lazy {
            read(DbHelper.table("dbtable.sailing_port_reward_anchors"),
                DbHelper.table("dbtable.sailing_bounty_rewards"),
                DbHelper.table("dbtable.sailing_courier_rewards"))
        }

        fun read(anchors: List<DbHelper>, bounties: List<DbHelper>, couriers: List<DbHelper>): PortTaskRewardData {
            require(anchors.isNotEmpty() && bounties.isNotEmpty() && couriers.isNotEmpty()) {
                "Sailing port reward data is missing; rebuild the server cache."
            }
            val anchorMap = anchors.associate { row ->
                val dock = row.column("dbcol.sailing_port_reward_anchors:dock", DbRowIdCodec)
                require(DbHelper.row(dock).tableId == "dbtable.sailing_dock".asRSCM()) {
                    "Sailing reward anchor ${row.id} does not reference a dock."
                }
                val coord = row.column("dbcol.sailing_port_reward_anchors:position", CoordGridCodec)
                require(coord.level == 0) { "Sailing reward anchors must be on the surface." }
                dock to PortTaskRewards.PortAnchor(coord.x, coord.z,
                    row.column("dbcol.sailing_port_reward_anchors:cache_derived", BooleanCodec))
            }
            val bountyMap = bounties.associate { row ->
                val npc = row.column("dbcol.sailing_bounty_rewards:target", NpcTypeCodec)
                val xp = row.column("dbcol.sailing_bounty_rewards:xp", IntCodec)
                require(xp >= 0) { "Sailing bounty XP cannot be negative: ${row.id}." }
                npc.id to xp
            }
            val courierMap = couriers.associate { row ->
                val task = row.column("dbcol.sailing_courier_rewards:task", DbRowIdCodec)
                require(DbHelper.row(task).tableId == "dbtable.port_task".asRSCM()) {
                    "Sailing courier reward ${row.id} does not reference a port task."
                }
                require(PortTaskRow.getRow(task).taskType == PortTaskRewards.TASK_TYPE_COURIER) {
                    "Sailing courier reward ${row.id} references a bounty."
                }
                val xp = row.column("dbcol.sailing_courier_rewards:xp", IntCodec)
                val coins = row.column("dbcol.sailing_courier_rewards:coins", IntCodec)
                require(xp >= 0 && coins >= 0) { "Sailing courier rewards cannot be negative: ${row.id}." }
                task to CourierReward(xp, coins)
            }
            require(anchorMap.size == anchors.size) { "Duplicate Sailing reward anchor docks." }
            require(bountyMap.size == bounties.size) { "Duplicate Sailing bounty reward targets." }
            require(courierMap.size == couriers.size) { "Duplicate Sailing courier reward tasks." }
            return PortTaskRewardData(anchorMap, bountyMap, courierMap)
        }
    }
}
