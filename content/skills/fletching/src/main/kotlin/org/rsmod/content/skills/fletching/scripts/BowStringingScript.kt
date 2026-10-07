package org.rsmod.content.skills.fletching.scripts

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

class BowStringingScript : PluginScript() {

    private val recipes = FletchingRecipe.table("fletching_stringing")

    override fun ScriptContext.startup() {
        for (recipe in recipes) {
            onOpHeldU(FletchingParams.BOW_STRING, recipe.inputs.first().internalName) {
                promptString(recipe)
            }
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.STRING_QUEUE) {
            processString(it.args)
        }
    }

    private suspend fun ProtectedAccess.promptString(recipe: FletchingRecipe) {
        if (!meetsFletchingLevel(recipe)) {
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                verb = "string",
                actionType = SkillingActionType.STRING,
                entries = listOf(recipe.toSkillMultiEntry()),
            )
        ) { selection ->
            val capped = minOf(selection.amount, maxCraftable(recipe))
            if (capped > 0) {
                clearWeakQueue(FletchingParams.STRING_QUEUE)
                // Start the baked-audio stringing loop once per batch; per-item resets clip it.
                recipe.anim?.internalName?.let(::anim)
                fletchQueue(
                    FletchingParams.STRING_QUEUE,
                    FletchingParams.STRING_TICKS,
                    FletchingTask(recipe, capped, completed = 0),
                )
            }
        }
    }

    private suspend fun ProtectedAccess.processString(task: FletchingTask) {
        val recipe = task.recipe
        if (!meetsFletchingLevel(recipe) || !hasMaterials(recipe)) {
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
            fletchQueue(
                FletchingParams.STRING_QUEUE,
                FletchingParams.STRING_TICKS,
                FletchingTask(recipe, task.amount, completed),
            )
        } else {
            resetAnim()
        }
    }
}
