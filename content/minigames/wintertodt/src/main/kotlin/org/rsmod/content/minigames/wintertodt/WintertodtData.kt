package org.rsmod.content.minigames.wintertodt

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.StatIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object WintertodtData {
    val rules by lazy {
        val row = DbHelper.table("dbtable.winter_rules").single()
        WinterRules(row.multiColumn("dbcol.winter_rules:settings", IntCodec),
            row.multiColumn("dbcol.winter_rules:bounds", IntCodec),
            row.column("dbcol.winter_rules:entry", CoordGridCodec),
            row.column("dbcol.winter_rules:exit", CoordGridCodec),
            row.column("dbcol.winter_rules:storm", CoordGridCodec)).also {
            require(it.settings.size == 10 && it.bounds.size == 5)
            require(it.maxEnergy > 0 && it.respawnTicks > 0 && it.minimumPoints > 0)
        }
    }
    val actions by lazy {
        DbHelper.table("dbtable.winter_actions").associate { row ->
            val name = row.column("dbcol.winter_actions:name", StringCodec)
            name to WinterAction(
                symbol(RSCMType.STAT, row.column("dbcol.winter_actions:stat", StatIdCodec)),
                row.column("dbcol.winter_actions:xp_tenths", IntCodec),
                row.column("dbcol.winter_actions:points", IntCodec),
                row.column("dbcol.winter_actions:ticks", IntCodec))
        }.also { require(it.size == 9) }
    }
    val camps by lazy {
        DbHelper.table("dbtable.winter_camps").sortedBy {
            it.column("dbcol.winter_camps:order", IntCodec)
        }.map { WinterCampData(it.column("dbcol.winter_camps:brazier", CoordGridCodec),
            it.column("dbcol.winter_camps:pyromancer", CoordGridCodec)) }
            .also { require(it.size == 4 && it.distinctBy(WinterCampData::brazier).size == 4) }
    }
    val warmItems by lazy {
        DbHelper.table("dbtable.winter_warm_items").single()
            .multiColumn("dbcol.winter_warm_items:items", ObjIdCodec).toSet()
    }
    val rewards by lazy {
        DbHelper.table("dbtable.winter_rewards").map { row ->
            val range = row.multiColumn("dbcol.winter_rewards:quantity", IntCodec)
            WinterMaterial(row.column("dbcol.winter_rewards:category", IntCodec),
                symbol(RSCMType.STAT, row.column("dbcol.winter_rewards:stat", StatIdCodec)),
                symbol(RSCMType.OBJ, row.column("dbcol.winter_rewards:item", ObjIdCodec)),
                range[0]..range[1], row.column("dbcol.winter_rewards:minimum_level", IntCodec))
        }.also { require(it.isNotEmpty() && it.all { r -> r.quantity.first > 0 }) }
    }
    val uniques by lazy {
        DbHelper.table("dbtable.winter_uniques").sortedBy {
            it.column("dbcol.winter_uniques:order", IntCodec)
        }.map { row ->
            val range = row.multiColumn("dbcol.winter_uniques:quantity", IntCodec)
            val replacement = row.multiColumn("dbcol.winter_uniques:replacement_quantity", IntCodec)
            WinterUnique(row.multiColumn("dbcol.winter_uniques:items", ObjIdCodec).map {
                symbol(RSCMType.OBJ, it)
            }, row.column("dbcol.winter_uniques:chance", IntCodec), range[0]..range[1],
                row.column("dbcol.winter_uniques:cap", IntCodec),
                symbol(RSCMType.OBJ, row.column("dbcol.winter_uniques:replacement", ObjIdCodec)),
                replacement[0]..replacement[1])
        }.also { require(it.size == 7 && it.all { r -> r.chance > 0 }) }
    }
    private fun symbol(type: RSCMType, id: Int) = RSCM.getReverseMapping(type, id)
}

internal data class WinterRules(
    val settings: List<Int>, val bounds: List<Int>, val entry: CoordGrid, val exit: CoordGrid,
    val storm: CoordGrid,
) {
    val level get() = settings[0]
    val maxEnergy get() = settings[1]
    val respawnTicks get() = settings[2]
    val energyDrain get() = settings[3]
    val coldTicks get() = settings[4]
    val regenTicks get() = settings[5]
    val warningTicks get() = settings[6]
    val rewardCap get() = settings[7]
    val minimumPoints get() = settings[8]
    val hazardDelay get() = settings[9]
    fun contains(c: CoordGrid) = c.level == bounds[4] && c.x in bounds[0]..bounds[2] &&
        c.z in bounds[1]..bounds[3]
    fun exposed(c: CoordGrid) = contains(c) && c.z >= 3986
}
internal data class WinterAction(val stat: String, val xpTenths: Int, val points: Int, val ticks: Int)
internal data class WinterCampData(val brazier: CoordGrid, val pyro: CoordGrid)
internal data class WinterMaterial(val category: Int, val stat: String, val obj: String,
    val quantity: IntRange, val level: Int)
internal data class WinterUnique(val items: List<String>, val chance: Int, val quantity: IntRange,
    val cap: Int, val replacement: String, val replacementQuantity: IntRange)
