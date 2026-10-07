package org.rsmod.content.skills.smithing.table

import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.multiColumnMixed
import dev.openrune.types.dbcol.obj

class SmithingCannonball(row: DbHelper) {

    val rowId: Int = row.id

    val input: ItemServerType = row.obj("dbcol.smithing_cannon_balls:input")

    val levelReq: Int =
        row.multiColumnMixed(
                "dbcol.smithing_cannon_balls:stat_req",
                DbColumnCodec.StatTypeCodec,
                DbColumnCodec.IntCodec,
            )[1]
            as Int

    val output: ItemServerType = row.obj("dbcol.smithing_cannon_balls:output")

    val xpTenths: Int = row.int("dbcol.smithing_cannon_balls:xp_tenths")

    val xpPerBar: Double
        get() = xpTenths / 10.0

    companion object {
        private val cachedAll: List<SmithingCannonball> by lazy {
            DbHelper.table("dbtable.smithing_cannon_balls").map { SmithingCannonball(it) }
        }

        fun all(): List<SmithingCannonball> = cachedAll
    }
}
