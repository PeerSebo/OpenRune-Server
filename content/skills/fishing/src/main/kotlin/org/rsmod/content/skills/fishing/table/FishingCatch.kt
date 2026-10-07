package org.rsmod.content.skills.fishing.table

import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.intOptional
import dev.openrune.types.dbcol.obj
import dev.openrune.types.dbcol.string

class FishingCatch(row: DbHelper) {

    val rowId: Int = row.id

    val style: Int = row.int("dbcol.$TABLE:style")

    val item: ItemServerType = row.obj("dbcol.$TABLE:fish_item")

    val level: Int = row.int("dbcol.$TABLE:level")

    // XP is stored in tenths.
    val xpTenths: Int = row.int("dbcol.$TABLE:xp_tenths")

    val low: Int = row.int("dbcol.$TABLE:success_rate_low")
    val high: Int = row.int("dbcol.$TABLE:success_rate_high")

    val dragonLow: Int? = row.intOptional("dbcol.$TABLE:dragon_rate_low")
    val dragonHigh: Int? = row.intOptional("dbcol.$TABLE:dragon_rate_high")

    val crystalLow: Int? = row.intOptional("dbcol.$TABLE:crystal_rate_low")
    val crystalHigh: Int? = row.intOptional("dbcol.$TABLE:crystal_rate_high")

    val rolls: Int = row.intOptional("dbcol.$TABLE:rolls") ?: 1

    // Add the flat success bonus before flooring interpolation.
    val bonus: Int = row.intOptional("dbcol.$TABLE:bonus") ?: 0

    val catchMessage: String = row.string("dbcol.$TABLE:catch_message")

    val agilityLevel: Int? = row.intOptional("dbcol.$TABLE:agility_level")

    val strengthLevel: Int? = row.intOptional("dbcol.$TABLE:strength_level")

    val secondaryXpTenths: Int? = row.intOptional("dbcol.$TABLE:secondary_xp_tenths")

    val countMin: Int = row.intOptional("dbcol.$TABLE:count_min") ?: 1
    val countMax: Int = row.intOptional("dbcol.$TABLE:count_max") ?: 1

    val xp: Double
        get() = xpTenths / 10.0

    val secondaryXp: Double
        get() = (secondaryXpTenths ?: 0) / 10.0

    companion object {
        private const val TABLE = "fishing_catches"

        private val byStyle: Map<Int, List<FishingCatch>> by lazy {
            DbHelper.table("dbtable.$TABLE").map(::FishingCatch).groupBy { it.style }
        }

        fun cascade(style: Int): List<FishingCatch> =
            byStyle[style].orEmpty().sortedByDescending { it.level }

        fun tableOrder(style: Int): List<FishingCatch> = byStyle[style].orEmpty()

        fun minLevel(style: Int): Int =
            byStyle[style].orEmpty().minOfOrNull { it.level }
                ?: error("No catch rows loaded for fishing style $style")

        fun allRows(): List<FishingCatch> = byStyle.values.flatten()
    }
}
