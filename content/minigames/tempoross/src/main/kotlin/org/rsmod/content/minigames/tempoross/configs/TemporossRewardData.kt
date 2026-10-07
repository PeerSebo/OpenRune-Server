package org.rsmod.content.minigames.tempoross.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.content.minigames.tempoross.TemporossRewards.Entry

internal object TemporossRewardData {
    val fishBands: List<FishBand> by lazy {
        DbHelper.table("dbtable.tempoross_fish_bands").map { row ->
            FishBand(
                level = row.column("dbcol.tempoross_fish_bands:level", IntCodec),
                fish = row.multiColumn("dbcol.tempoross_fish_bands:fish", ItemServerTypeCodec)
                    .map { RSCM.getReverseMapping(RSCMType.OBJ, it.id) },
            )
        }.sortedBy { it.level }.also { bands ->
            require(bands.isNotEmpty()) { "Tempoross fish bands are missing; rebuild the server cache." }
            require(bands.distinctBy { it.level }.size == bands.size)
            require(bands.all { it.fish.size == fishSlots.size })
        }
    }

    val fishSlots: List<FishSlot> by lazy {
        DbHelper.table("dbtable.tempoross_fish_slots").map { row ->
            FishSlot(
                order = row.column("dbcol.tempoross_fish_slots:roll_order", IntCodec),
                quantity = row.column("dbcol.tempoross_fish_slots:minimum", IntCodec)..row.column("dbcol.tempoross_fish_slots:maximum", IntCodec),
                weight = row.column("dbcol.tempoross_fish_slots:weight", IntCodec),
            )
        }.sortedBy { it.order }.also { slots ->
            require(slots.isNotEmpty()) { "Tempoross fish slots are missing; rebuild the server cache." }
            require(slots.map { it.order } == slots.indices.toList())
            require(slots.all { it.weight > 0 && it.quantity.first > 0 && !it.quantity.isEmpty() })
        }
    }

    val casketRewards: List<Entry> by lazy {
        val rows = DbHelper.table("dbtable.tempoross_casket_rewards")
            .sortedBy { it.column("dbcol.tempoross_casket_rewards:roll_order", IntCodec) }
        require(rows.isNotEmpty()) { "Tempoross casket rewards are missing; rebuild the server cache." }
        require(rows.map { it.column("dbcol.tempoross_casket_rewards:roll_order", IntCodec) } == rows.indices.toList())
        rows.map { row ->
            Entry(
                obj = RSCM.getReverseMapping(
                    RSCMType.OBJ,
                    row.column("dbcol.tempoross_casket_rewards:reward", ItemServerTypeCodec).id,
                ),
                quantity = row.column("dbcol.tempoross_casket_rewards:minimum", IntCodec)..row.column("dbcol.tempoross_casket_rewards:maximum", IntCodec),
                weight = row.column("dbcol.tempoross_casket_rewards:weight", IntCodec),
                noted = row.column("dbcol.tempoross_casket_rewards:noted", BooleanCodec),
            )
        }.also { rewards ->
            require(rewards.all { it.weight > 0 && it.quantity.first > 0 && !it.quantity.isEmpty() })
        }
    }

    data class FishBand(val level: Int, val fish: List<String>)
    data class FishSlot(val order: Int, val quantity: IntRange, val weight: Int)
}
