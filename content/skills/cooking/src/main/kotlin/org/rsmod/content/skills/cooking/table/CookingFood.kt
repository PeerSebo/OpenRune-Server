package org.rsmod.content.skills.cooking.table

import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.intOptional
import dev.openrune.types.dbcol.obj
import dev.openrune.types.dbcol.statReq
import dev.openrune.types.dbcol.string

class CookingFood(row: DbHelper) {

    val rowId: Int = row.id

    val raw: ItemServerType = row.obj("dbcol.$TABLE:input")

    val cooked: ItemServerType = row.obj("dbcol.$TABLE:output")

    val burnt: ItemServerType = row.obj("dbcol.$TABLE:burnt")

    val level: Int = row.statReq("dbcol.$TABLE:stat_req").level

    // XP is stored in tenths.
    val xpTenths: Int = row.int("dbcol.$TABLE:xp_tenths")

    val outputAmount: Int = row.int("dbcol.$TABLE:output_amount")

    val category: String = row.string("dbcol.$TABLE:category")

    private val lowBase: Int = row.int("dbcol.$TABLE:low_base")
    private val highBase: Int = row.int("dbcol.$TABLE:high_base")

    private val lowRange: Int? = row.intOptional("dbcol.$TABLE:low_range")
    private val highRange: Int? = row.intOptional("dbcol.$TABLE:high_range")

    private val lowGauntlet: Int? = row.intOptional("dbcol.$TABLE:low_gauntlet")
    private val highGauntlet: Int? = row.intOptional("dbcol.$TABLE:high_gauntlet")

    private val lowLumbridge: Int? = row.intOptional("dbcol.$TABLE:low_lumbridge")
    private val highLumbridge: Int? = row.intOptional("dbcol.$TABLE:high_lumbridge")

    val xp: Double
        get() = xpTenths / 10.0

    val gauntletAffected: Boolean
        get() = lowGauntlet != null && highGauntlet != null

    fun burnCurve(context: CookingBurnContext, gauntlets: Boolean = false): CookingBurnCurve {
        val low: Int
        val high: Int
        when {
            gauntlets && lowGauntlet != null && highGauntlet != null -> {
                low = lowGauntlet
                high = highGauntlet
            }
            context == CookingBurnContext.RANGE_LUMBRIDGE &&
                lowLumbridge != null &&
                highLumbridge != null -> {
                low = lowLumbridge
                high = highLumbridge
            }
            context.isRange && lowRange != null && highRange != null -> {
                low = lowRange
                high = highRange
            }
            else -> {
                low = lowBase
                high = highBase
            }
        }
        return CookingBurnCurve(low, high, context.successBonus)
    }

    override fun toString(): String = "CookingFood(row=$rowId, raw=${raw.internalName})"

    companion object {
        private const val TABLE = "cooking_foods"

        private val cachedAll: List<CookingFood> by lazy {
            DbHelper.table("dbtable.$TABLE").map(::CookingFood)
        }

        fun all(): List<CookingFood> = cachedAll
    }
}

data class CookingBurnCurve(val low: Int, val high: Int, val successBonus: Double = 0.0)

enum class CookingBurnContext(val successBonus: Double) {
    FIRE(0.0),
    RANGE(0.0),
    RANGE_LUMBRIDGE(0.0),

    RANGE_HOSIDIUS(HOSIDIUS_EASY_BONUS),

    RANGE_HOSIDIUS_ELITE(HOSIDIUS_ELITE_BONUS);

    val isRange: Boolean
        get() = this != FIRE
}

const val HOSIDIUS_EASY_BONUS: Double = 12.8

const val HOSIDIUS_ELITE_BONUS: Double = 25.6
