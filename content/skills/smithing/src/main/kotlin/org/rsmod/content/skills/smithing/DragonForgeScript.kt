package org.rsmod.content.skills.smithing

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.smithing.SmithingDragonForgeRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.smithing.configs.SmithingParams
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.content.skills.smithing.util.SmithingUtils.hasHammer
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DragonForgeScript : PluginScript() {

    private val sailingEraOutputs =
        setOf("obj.sailing_boat_keel_part_dragon", "obj.sailing_boat_large_keel_part_dragon")

    private val forgeRecipes =
        SmithingDragonForgeRow.all().filterNot { it.output.internalName in sailingEraOutputs }

    private val recipesByOutput = forgeRecipes.associateBy { it.output.internalName }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.ds2_ac_forge_anvil") { openDragonForge() }

        onPlayerQueueWithArgs<DragonForgeTask>(SmithingParams.DRAGON_FORGE_QUEUE) {
            processForgeTask(it.args)
        }
    }

    private suspend fun ProtectedAccess.openDragonForge() {
        val available = forgeRecipes.filter { player.smithingLvl >= it.statReq.first().t1 }
        if (available.isEmpty()) {
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                actionType = SkillingActionType.SMELT,
                entries = available.map(::toEntry),
                maxCountProvider = { inventory, entry ->
                    recipesByOutput[entry.internal]?.let { maxSmeltCount(inventory, it) } ?: 0
                },
            )
        ) { selection ->
            val recipe = recipesByOutput[selection.entry.internal] ?: return@openSkillMulti
            startSmelting(recipe, selection.amount)
        }
    }

    private fun toEntry(recipe: SmithingDragonForgeRow): SkillMultiEntry =
        SkillMultiEntry(
            recipe.output.internalName,
            recipe.input.mapIndexed { index, item ->
                Material(item.internalName, recipe.inputAmount.getOrElse(index) { 1 })
            },
        )

    private suspend fun ProtectedAccess.startSmelting(
        recipe: SmithingDragonForgeRow,
        requestedAmount: Int,
    ) {
        val amount = minOf(requestedAmount, maxSmeltCount(inv, recipe))
        if (amount <= 0) {
            return
        }

        if (!canSmelt(recipe)) {
            return
        }

        clearWeakQueue(SmithingParams.DRAGON_FORGE_QUEUE)
        scheduleForgeCycle(DragonForgeTask(recipe, amount, completed = 0))
    }

    private fun ProtectedAccess.scheduleForgeCycle(task: DragonForgeTask) {
        anim(SmithingParams.FURNACE_ANIM)
        soundSynth(SmithingParams.FURNACE_SOUND)
        smithQueue(SmithingParams.DRAGON_FORGE_QUEUE, SmithingParams.DRAGON_FORGE_TICKS, task)
    }

    private suspend fun ProtectedAccess.processForgeTask(task: DragonForgeTask) {
        if (!canSmelt(task.recipe)) {
            resetAnim()
            return
        }

        if (!performSmelt(task.recipe)) {
            resetAnim()
            return
        }

        val completed = task.completed + 1
        if (completed < task.amount && canSmelt(task.recipe)) {
            scheduleForgeCycle(task.copy(completed = completed))
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.performSmelt(recipe: SmithingDragonForgeRow): Boolean {
        val inputs =
            recipe.input.mapIndexed { index, item ->
                item.internalName to recipe.inputAmount.getOrElse(index) { 1 }
            }
        if (!tryProduce(inputs, recipe.output.internalName, recipe.outputAmount)) {
            return false
        }
        statAdvance("stat.smithing", recipe.xp.toDouble())
        val inputName = SmithingUtils.itemName(recipe.input.first(), "ore")
        mes("You smelt the $inputName in the furnace.")
        return true
    }

    private suspend fun ProtectedAccess.canSmelt(recipe: SmithingDragonForgeRow): Boolean {
        if (!hasHammer()) {
            mes("You need a hammer to work metal with an anvil.")
            return false
        }

        val missing =
            recipe.input.mapIndexedNotNull { index, input ->
                val amount = recipe.inputAmount.getOrElse(index) { 1 }
                val need = amount - inv.count(input.internalName)
                if (need > 0) {
                    SmithingUtils.itemName(input) to need
                } else {
                    null
                }
            }

        if (missing.isNotEmpty()) {
            val missingText =
                missing.joinToString(", ") { (name, amount) ->
                    "${SmithingUtils.countLiteral(amount)} ${name.lowercase()}"
                }
            val outputName = SmithingUtils.itemName(recipe.output, "bar")
            mesbox("You need $missingText to make ${SmithingUtils.prefixAn(outputName)}.")
            return false
        }

        return SmithingUtils.requireSmithingLevel(
            this,
            recipe.statReq.first().t1,
            "smelt ${SmithingUtils.itemName(recipe.input.first(), "ore")}",
        )
    }

    private fun maxSmeltCount(inventory: Inventory, recipe: SmithingDragonForgeRow): Int =
        recipe.input
            .mapIndexed { index, input ->
                val amount = recipe.inputAmount.getOrElse(index) { 1 }
                inventory.count(input.internalName) / amount
            }
            .minOrNull() ?: 0

    private data class DragonForgeTask(
        val recipe: SmithingDragonForgeRow,
        val amount: Int,
        val completed: Int,
    )
}
