package org.rsmod.content.skills.hunter.configs

import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object FalconryRules {
    private val row by lazy { DbHelper.table("dbtable.hunter_falconry_rules").single() }
    private val fees by lazy { row.multiColumn("dbcol.hunter_falconry_rules:fees", IntCodec) }
    private val flight by lazy { row.multiColumn("dbcol.hunter_falconry_rules:flight", IntCodec) }
    private val timeouts by lazy { row.multiColumn("dbcol.hunter_falconry_rules:timeouts", IntCodec) }
    private val bounds by lazy { row.multiColumn("dbcol.hunter_falconry_rules:bounds", IntCodec) }
    val rentalFee get() = fees[0]
    val investment get() = fees[1]
    val range get() = flight[0]
    val catchTimeout get() = timeouts[0]
    val preyRespawn get() = timeouts[1]

    fun flightTicks(distance: Int) = ((distance + flight[1] - 1) / flight[1]).coerceAtLeast(flight[2])
    fun contains(coords: CoordGrid) = coords.level == bounds[4] &&
        coords.x in bounds[0]..bounds[2] && coords.z in bounds[1]..bounds[3]
}
