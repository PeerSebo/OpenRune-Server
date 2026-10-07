package org.rsmod.content.minigames.giantsfoundry.configs

import dev.openrune.ServerCacheManager
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import org.rsmod.api.random.GameRandom

object FoundryOrePackData {
    const val PACK = "obj.giants_foundry_ore_pack"
    const val ROLLS = 30

    val outcomes: List<FoundryOreOutcome> by lazy {
        DbHelper.table("dbtable.giants_foundry_ore_pack_outcomes").map {
            FoundryOreOutcome(
                it.column("dbcol.giants_foundry_ore_pack_outcomes:ore", ObjIdCodec),
                it.column("dbcol.giants_foundry_ore_pack_outcomes:weight", IntCodec),
            ).also { ore ->
                require(ore.weight > 0)
                require(requireNotNull(ServerCacheManager.getItem(ore.item)).certlink > 0)
            }
        }.also {
            require(it.size == 5 && it.map(FoundryOreOutcome::item).distinct().size == 5)
            require(it.sumOf(FoundryOreOutcome::weight) == 10_000)
        }
    }

    fun roll(random: GameRandom): Map<Int, Int> = buildMap {
        repeat(ROLLS) {
            val item = outcome(random.of(10_000))
            put(item, getOrDefault(item, 0) + 1)
        }
    }

    internal fun outcome(roll: Int): Int {
        require(roll in 0 until 10_000)
        var remaining = roll
        for (outcome in outcomes) {
            if (remaining < outcome.weight) return outcome.item
            remaining -= outcome.weight
        }
        error("Invalid Foundry ore pack distribution")
    }
}

data class FoundryOreOutcome(val item: Int, val weight: Int)
