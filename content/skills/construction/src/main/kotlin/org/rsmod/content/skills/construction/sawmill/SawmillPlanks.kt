package org.rsmod.content.skills.construction.sawmill

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.obj
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankSack
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankType
import org.rsmod.game.entity.Player

internal data class SawmillPlank(val logs: ItemServerType, val plank: ItemServerType, val coins: Int)

internal object SawmillPlanks {
    val recipes: List<SawmillPlank> by lazy {
        DbHelper.table("dbtable.sawmill_planks").map {
            SawmillPlank(
                it.obj("dbcol.sawmill_planks:logs"),
                it.obj("dbcol.sawmill_planks:plank"),
                it.int("dbcol.sawmill_planks:coins"),
            )
        }.sortedBy { it.coins }.also {
            require(it.isNotEmpty() && it.all { recipe -> recipe.coins > 0 })
            require(it.map { recipe -> recipe.logs.id }.distinct().size == it.size)
        }
    }

    fun convert(player: Player, recipe: SawmillPlank, requested: Int): Int {
        if (requested <= 0) return 0
        val held = player.inv.physicalCount(recipe.logs.internalName)
        val affordable = player.inv.physicalCount(COINS) / recipe.coins
        if (held == 0) {
            player.mes("You need unnoted ${recipe.logs.name.lowercase()} to make those planks.")
            return 0
        }
        if (affordable == 0) {
            player.mes("You don't have enough coins to pay for those planks.")
            return 0
        }
        val vouchers = player.inv.physicalCount(VOUCHER)
        val plankType = requireNotNull(PlankType.ofObj(recipe.plank.internalName))
        val sackSpace = if (PlankSack.isOwned(player)) PlankSack.freeSpace(player) else 0
        for (amount in minOf(requested, held, affordable) downTo 1) {
            val bonus = minOf(amount, vouchers)
            val toSack = minOf(amount + bonus, sackSpace)
            val transaction = player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                delete(inventory, COINS.asRSCM(), amount * recipe.coins)
                delete(inventory, recipe.logs.id, amount)
                if (bonus > 0) delete(inventory, VOUCHER.asRSCM(), bonus)
                val toInventory = amount + bonus - toSack
                if (toInventory > 0) add(inventory, recipe.plank.id, toInventory)
            }
            if (transaction.failure) continue
            if (toSack > 0) check(PlankSack.depositUpTo(player, plankType, toSack) == toSack)
            player.mes("The sawmill operator converts your logs into planks.")
            return amount
        }
        player.mes("You don't have enough inventory space for the planks.")
        return 0
    }

    const val COINS = "obj.coins"
    const val VOUCHER = "obj.forestry_sawmill_voucher"
}
