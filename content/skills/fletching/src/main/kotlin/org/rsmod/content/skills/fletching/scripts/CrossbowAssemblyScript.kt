package org.rsmod.content.skills.fletching.scripts

import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.fletching.configs.FletchingParams
import org.rsmod.content.skills.fletching.table.FletchingRecipe
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CrossbowAssemblyScript : PluginScript() {

    private val recipes =
        FletchingRecipe.table("fletching_crossbows").filter { it.category == "Attach" }

    override fun ScriptContext.startup() {
        for (recipe in recipes) {
            onOpHeldU(recipe.inputs[0].internalName, recipe.inputs[1].internalName) {
                promptAssemble(recipe)
            }
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.XBOW_ASSEMBLE_QUEUE) {
            processAssemble(it.args)
        }
    }

    private suspend fun ProtectedAccess.promptAssemble(recipe: FletchingRecipe) {
        if (!meetsFletchingLevel(recipe)) {
            return
        }
        if (!hasHammer(recipe)) {
            mesbox("You need a hammer to attach the limbs to the stock.")
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                actionType = SkillingActionType.MAKE,
                entries = listOf(recipe.toSkillMultiEntry()),
            )
        ) { selection ->
            val capped = minOf(selection.amount, maxCraftable(recipe))
            if (capped > 0) {
                clearWeakQueue(FletchingParams.XBOW_ASSEMBLE_QUEUE)
                playAssembleAnim(recipe)
                fletchQueue(
                    FletchingParams.XBOW_ASSEMBLE_QUEUE,
                    FletchingParams.XBOW_ASSEMBLE_TICKS,
                    FletchingTask(recipe, capped, completed = 0),
                )
            }
        }
    }

    private fun ProtectedAccess.playAssembleAnim(recipe: FletchingRecipe) {
        recipe.anim?.internalName?.let(::anim)
        soundSynth(FletchingParams.FLETCH_CROSSBOW_SOUND)
    }

    private suspend fun ProtectedAccess.processAssemble(task: FletchingTask) {
        val recipe = task.recipe
        if (!meetsFletchingLevel(recipe) || !hasHammer(recipe) || !hasMaterials(recipe)) {
            resetAnim()
            return
        }

        if (!tryCraft(recipe)) {
            resetAnim()
            return
        }
        statAdvance(FLETCHING_STAT, recipe.xp)

        val completed = task.completed + 1
        if (completed < task.amount && hasMaterials(recipe)) {
            playAssembleAnim(recipe)
            fletchQueue(
                FletchingParams.XBOW_ASSEMBLE_QUEUE,
                FletchingParams.XBOW_ASSEMBLE_TICKS,
                FletchingTask(recipe, task.amount, completed),
            )
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.hasHammer(recipe: FletchingRecipe): Boolean {
        return inv.contains(recipe.hammerTool.internalName)
    }

    private val FletchingRecipe.hammerTool: ItemServerType
        get() = checkNotNull(tool) { "Missing `tool` column for crossbow-assembly row `$rowId`." }
}
