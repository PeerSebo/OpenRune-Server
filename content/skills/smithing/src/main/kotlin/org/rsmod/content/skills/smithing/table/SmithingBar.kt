package org.rsmod.content.skills.smithing.table

import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.intOptional
import dev.openrune.types.dbcol.list
import dev.openrune.types.dbcol.multiColumnMixed
import dev.openrune.types.dbcol.obj
import dev.openrune.types.dbcol.string

class SmithingBar(row: DbHelper) {

    val rowId: Int = row.id

    val input: List<ItemServerType> =
        row.list("dbcol.smithing_bars:input", DbColumnCodec.ItemServerTypeCodec)

    val inputAmount: List<Int> =
        row.list("dbcol.smithing_bars:input_amount", DbColumnCodec.IntCodec)

    val levelReq: Int =
        row.multiColumnMixed(
                "dbcol.smithing_bars:stat_req",
                DbColumnCodec.StatTypeCodec,
                DbColumnCodec.IntCodec,
            )[1]
            as Int

    val output: ItemServerType = row.obj("dbcol.smithing_bars:output")

    val prefix: String = row.string("dbcol.smithing_bars:prefix")

    val smeltXpTenths: Int = row.int("dbcol.smithing_bars:xp_tenths")

    val smithXpTenths: Int = row.int("dbcol.smithing_bars:smith_xp_tenths")

    val smeltXpAlternateTenths: Int? = row.intOptional("dbcol.smithing_bars:xp_alternate_tenths")

    val smeltXp: Double
        get() = smeltXpTenths / 10.0

    val smithXp: Double
        get() = smithXpTenths / 10.0

    val smeltXpAlternate: Double?
        get() = smeltXpAlternateTenths?.let { it / 10.0 }

    companion object {
        private val cachedAll: List<SmithingBar> by lazy {
            DbHelper.table("dbtable.smithing_bars").map { SmithingBar(it) }
        }

        fun all(): List<SmithingBar> = cachedAll
    }
}
