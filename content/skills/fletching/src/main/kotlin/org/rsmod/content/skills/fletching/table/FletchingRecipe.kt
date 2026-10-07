package org.rsmod.content.skills.fletching.table

import dev.openrune.types.ItemServerType
import dev.openrune.types.SequenceServerType
import dev.openrune.types.StatRequirement
import dev.openrune.types.StatType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.columnOptional
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.intOptional
import dev.openrune.types.dbcol.list
import dev.openrune.types.dbcol.multiColumnMixed
import dev.openrune.types.dbcol.obj
import dev.openrune.types.dbcol.objOptional
import dev.openrune.types.dbcol.string

class FletchingRecipe(row: DbHelper, table: String) {

    val rowId: Int = row.id

    val inputs: List<ItemServerType> =
        row.list("dbcol.$table:input", DbColumnCodec.ItemServerTypeCodec)

    val inputAmounts: List<Int> = row.list("dbcol.$table:input_amount", DbColumnCodec.IntCodec)

    val statRequirements: List<StatRequirement> =
        row.multiColumnMixed(
                "dbcol.$table:stat_req",
                DbColumnCodec.StatTypeCodec,
                DbColumnCodec.IntCodec,
            ).chunked(2).map { StatRequirement(it[0] as StatType, it[1] as Int) }

    val levelReq: Int = statRequirements.first { it.stat.isType("stat.fletching") }.level

    // XP is in tenths, per individual ammo item.
    val xpTenths: Int = row.int("dbcol.$table:xp_tenths")

    val output: ItemServerType = row.obj("dbcol.$table:output")

    val outputAmount: Int = row.int("dbcol.$table:output_amount")

    val category: String = row.string("dbcol.$table:category")

    val anim: SequenceServerType? = row.columnOptional("dbcol.$table:anim", DbColumnCodec.SeqCodec)

    val ticks: Int? = row.intOptional("dbcol.$table:ticks")

    val knifeTicks: Int? = if (table == "fletching_carving") {
        row.intOptional("dbcol.$table:knife_ticks")
    } else null

    val tool: ItemServerType? = row.objOptional("dbcol.$table:tool")

    val setSize: Int? = row.intOptional("dbcol.$table:set_size")

    val xp: Double
        get() = xpTenths / 10.0

    val materials: List<Pair<ItemServerType, Int>>
        get() = inputs.zip(inputAmounts)

    companion object {
        fun table(table: String): List<FletchingRecipe> =
            DbHelper.table("dbtable.$table").map { FletchingRecipe(it, table) }
    }
}
