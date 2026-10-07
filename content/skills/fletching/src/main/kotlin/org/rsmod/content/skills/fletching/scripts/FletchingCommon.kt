package org.rsmod.content.skills.fletching.scripts

import dev.openrune.types.ItemServerType
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.fletching.configs.FletchingParams
import org.rsmod.content.skills.fletching.table.FletchingRecipe
import org.rsmod.content.skills.slayer.core.SlayerTaskManager
import org.rsmod.game.inv.Inventory

internal data class FletchingTask(
    val recipe: FletchingRecipe,
    val amount: Int,
    val completed: Int,
    val usedFeather: ItemServerType? = null,
)

internal const val FLETCHING_STAT = "stat.fletching"

internal fun ProtectedAccess.maxCraftable(materials: List<Pair<ItemServerType, Int>>): Int =
    materials.minOf { (item, amount) -> inv.count(item.internalName) / amount }

internal fun ProtectedAccess.maxCraftable(recipe: FletchingRecipe): Int =
    maxCraftable(recipe.materials)

internal fun ProtectedAccess.hasMaterials(materials: List<Pair<ItemServerType, Int>>): Boolean =
    maxCraftable(materials) > 0

internal fun ProtectedAccess.hasMaterials(recipe: FletchingRecipe): Boolean =
    hasMaterials(recipe.materials)

// A trailing partial set counts as one action.
internal fun maxSets(inv: Inventory, materials: List<Pair<ItemServerType, Int>>, setSize: Int): Int {
    val singles = materials.minOf { (item, amount) -> inv.count(item.internalName) / amount }
    return (singles + setSize - 1) / setSize
}

internal fun maxSets(inv: Inventory, recipe: FletchingRecipe, setSize: Int): Int =
    maxSets(inv, recipe.materials, setSize)

internal fun FletchingRecipe.effectiveMaterials(
    usedFeather: ItemServerType?
): List<Pair<ItemServerType, Int>> {
    if (usedFeather == null) {
        return materials
    }
    return materials.map { (item, amount) ->
        if (item.internalName == FletchingParams.FEATHER) usedFeather to amount else item to amount
    }
}

internal fun ProtectedAccess.hasLevelFor(recipe: FletchingRecipe): Boolean =
    recipe.statRequirements.all { statBase(it.stat.internalName) >= it.level }

internal suspend fun ProtectedAccess.meetsFletchingLevel(recipe: FletchingRecipe): Boolean {
    if (recipe.inputs.any { it.internalName == "obj.slayer_broad_arrowhead" } &&
        !SlayerTaskManager.hasUnlockedReward(this, FletchingParams.BROADER_FLETCHING_BIT)) {
        mesbox(FletchingParams.BROADER_FLETCHING_LOCKED_MESSAGE)
        return false
    }
    val missing = recipe.statRequirements.firstOrNull { statBase(it.stat.internalName) < it.level }
        ?: return true
    mesbox("You need a ${missing.stat.displayName} level of ${missing.level}.")
    return false
}

// Queue/callback additions decrement immediately; schedule cadenceTicks + 1.
internal fun ProtectedAccess.fletchQueue(queue: String, cadenceTicks: Int, task: FletchingTask) {
    weakQueue(queue, cadenceTicks + 1, task)
}

internal fun FletchingRecipe.toSkillMultiEntry(perSetCount: Int? = null): SkillMultiEntry =
    SkillMultiEntry(
        internal = output.internalName,
        materials = materials.map { (item, amount) -> Material(item.internalName, amount) },
        perSetCount = perSetCount,
    )

internal fun ProtectedAccess.tryCraft(
    recipe: FletchingRecipe,
    materials: List<Pair<ItemServerType, Int>> = recipe.materials,
    count: Int = 1,
): Boolean {
    require(count > 0)
    if (maxCraftable(materials) < count) return false
    val result = player.invTransaction(inv) {
        val inventory = select(inv)
        for ((item, amount) in materials) {
            delete { from = inventory; obj = item.id; strictCount = amount * count }
        }
        insert { into = inventory; obj = recipe.output.id; strictCount = recipe.outputAmount * count }
    }
    if (result.failure) {
        mes(Constants.dm_invspace)
        return false
    }
    return true
}
