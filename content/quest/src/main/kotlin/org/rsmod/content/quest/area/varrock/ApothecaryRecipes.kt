package org.rsmod.content.quest.area.varrock

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetObj
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.objtx.Transaction
import org.rsmod.objtx.TransactionInventory

internal fun Player.populateApothecaryPotions() {
    for (recipe in ApothecaryRecipes.all) {
        ifSetObj("${recipe.component}_pic", recipe.product, 1)
        ifSetText("${recipe.component}_title", recipe.label)
        ifSetText("${recipe.component}_contents", recipe.requirements)
        ifSetEvents(recipe.component, -1..-1, IfEvent.Op1)
    }
}

internal fun Player.reportMissingApothecaryIngredients(recipe: ApothecaryRecipe): Boolean {
    val missing = recipe.ingredients.mapNotNull { ingredient ->
        val shortfall = ingredient.count - inv.count(ingredient.obj)
        if (shortfall > 0) "${ingredient.name} ($shortfall)" else null
    }
    if (missing.isEmpty()) return false
    mes("You still need: ${missing.joinToString(", ")}.")
    return true
}

internal fun Transaction<InvObj>.brewApothecaryPotion(
    inventory: TransactionInventory<InvObj>,
    recipe: ApothecaryRecipe,
) {
    for (ingredient in recipe.ingredients) {
        delete { from = inventory; obj = ingredient.obj.asRSCM(); strictCount = ingredient.count }
    }
    insert { into = inventory; obj = recipe.product.asRSCM(); strictCount = 1 }
}

internal data class ApothecaryIngredient(val obj: String, val count: Int, val name: String)

internal data class ApothecaryRecipe(
    val component: String,
    val product: String,
    val label: String,
    val ingredients: List<ApothecaryIngredient>,
) {
    val requirements: String get() = ingredients.joinToString("<br>") { "${it.count} ${it.name}" }
}

internal object ApothecaryRecipes {
    val all: List<ApothecaryRecipe> by lazy {
        DbHelper.table("dbtable.apothecary_potions").map { row ->
            val key = row.column("dbcol.apothecary_potions:key", StringCodec)
            val items = row.multiColumn("dbcol.apothecary_potions:items", ObjIdCodec)
            val counts = row.multiColumn("dbcol.apothecary_potions:counts", IntCodec)
            val names = row.multiColumn("dbcol.apothecary_potions:names", StringCodec)
            require(key in setOf("strength", "energy", "antipoison"))
            require(items.isNotEmpty() && items.size == counts.size && counts.size == names.size)
            require(counts.all { it > 0 })
            ApothecaryRecipe(
                "component.apothecary_potions:$key",
                RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.apothecary_potions:product", ObjIdCodec)),
                row.column("dbcol.apothecary_potions:title", StringCodec),
                items.indices.map { i ->
                    ApothecaryIngredient(RSCM.getReverseMapping(RSCMType.OBJ, items[i]), counts[i], names[i])
                },
            )
        }.also { recipes ->
            require(recipes.size == 3 && recipes.distinctBy { it.component }.size == 3)
        }
    }
}
