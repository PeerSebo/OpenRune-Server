package org.rsmod.content.minigames.mahoganyhomes.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.NpcIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn

internal object MahoganyHomesData {
    val tiers: Map<String, Tier> by lazy {
        DbHelper.table("dbtable.mahogany_homes_tiers").map { row ->
            Tier(
                order = row.column("dbcol.mahogany_homes_tiers:order", IntCodec),
                key = row.column("dbcol.mahogany_homes_tiers:key", StringCodec),
                level = row.column("dbcol.mahogany_homes_tiers:level", IntCodec),
                plank = RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.mahogany_homes_tiers:plank", ObjIdCodec)),
                buildXpTenths = row.column("dbcol.mahogany_homes_tiers:build_xp_tenths", IntCodec),
                repairXpTenths = row.column("dbcol.mahogany_homes_tiers:repair_xp_tenths", IntCodec),
                completionXpTenths = row.column("dbcol.mahogany_homes_tiers:completion_xp_tenths", IntCodec),
                points = row.column("dbcol.mahogany_homes_tiers:points", IntCodec),
            )
        }.sortedBy { it.order }.also(::validateTiers).associateBy { it.key }
    }

    val houses: Map<String, House> by lazy {
        DbHelper.table("dbtable.mahogany_homes_houses").map { row ->
            House(
                order = row.column("dbcol.mahogany_homes_houses:order", IntCodec),
                key = row.column("dbcol.mahogany_homes_houses:key", StringCodec),
                client = RSCM.getReverseMapping(RSCMType.NPC, row.column("dbcol.mahogany_homes_houses:client", NpcIdCodec)),
                hotspots = row.multiColumn("dbcol.mahogany_homes_houses:hotspots", LocIdCodec)
                    .map { RSCM.getReverseMapping(RSCMType.LOC, it) },
            )
        }.sortedBy { it.order }.also(::validateHouses).associateBy { it.key }
    }

    val rewards: List<Reward> by lazy {
        DbHelper.table("dbtable.mahogany_homes_rewards").map { row ->
            Reward(
                order = row.column("dbcol.mahogany_homes_rewards:order", IntCodec),
                item = RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.mahogany_homes_rewards:item", ObjIdCodec)),
                points = row.column("dbcol.mahogany_homes_rewards:points", IntCodec),
            )
        }.sortedBy { it.order }.also(::validateRewards)
    }

    val rewardPrices: Map<String, Int> by lazy { rewards.associate { it.item to it.points } }

    fun validateTiers(rows: List<Tier>) {
        require(rows.map { it.order } == rows.indices.toList()) { "Mahogany Homes tier order is invalid." }
        require(rows.map { it.key } == MahoganyHomesTier.entries.map { it.name }) {
            "Mahogany Homes tiers must preserve saved ordinal identities; rebuild the server cache."
        }
        require(rows.all { it.level in 1..99 && it.plank.startsWith("obj.") })
        require(rows.all { it.buildXpTenths > 0 && it.repairXpTenths > 0 && it.completionXpTenths > 0 && it.points > 0 })
        require(rows.map { it.level }.zipWithNext().all { (left, right) -> left < right })
    }

    fun validateHouses(rows: List<House>) {
        require(rows.map { it.order } == rows.indices.toList()) { "Mahogany Homes house order is invalid." }
        require(rows.map { it.key } == MahoganyHomesHouse.entries.map { it.name }) {
            "Mahogany Homes houses must preserve saved ordinal identities; rebuild the server cache."
        }
        require(rows.all { it.client.startsWith("npc.") && it.hotspots.size in 1..8 })
        require(rows.distinctBy { it.client }.size == rows.size) { "Mahogany Homes clients must be unique." }
        val hotspots = rows.flatMap { it.hotspots }
        require(hotspots.all { it.startsWith("loc.") } && hotspots.distinct().size == hotspots.size) {
            "Mahogany Homes hotspots must be unique loc references."
        }
    }

    fun validateRewards(rows: List<Reward>) {
        require(rows.isNotEmpty()) { "Mahogany Homes rewards are missing; rebuild the server cache." }
        require(rows.map { it.order } == rows.indices.toList()) { "Mahogany Homes reward order is invalid." }
        require(rows.all { it.item.startsWith("obj.") && it.points > 0 })
        require(rows.distinctBy { it.item }.size == rows.size) { "Mahogany Homes rewards must be unique." }
    }

    data class Tier(
        val order: Int,
        val key: String,
        val level: Int,
        val plank: String,
        val buildXpTenths: Int,
        val repairXpTenths: Int,
        val completionXpTenths: Int,
        val points: Int,
    )

    data class House(val order: Int, val key: String, val client: String, val hotspots: List<String>)

    data class Reward(val order: Int, val item: String, val points: Int)
}
