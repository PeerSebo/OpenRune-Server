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

class GemBoltTippingScript : PluginScript() {

    private val recipes = FletchingRecipe.table("fletching_bolt_tips")

    override fun ScriptContext.startup() {
        for (recipe in recipes) {
            onOpHeldU(FletchingParams.CHISEL, recipe.inputs.first().internalName) {
                promptTip(recipe)
            }
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.BOLT_TIP_QUEUE) { processTip(it.args) }
    }

    private suspend fun ProtectedAccess.promptTip(recipe: FletchingRecipe) {
        if (!meetsFletchingLevel(recipe)) {
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                verb = "cut",
                actionType = SkillingActionType.CUT,
                entries = listOf(recipe.toSkillMultiEntry()),
            )
        ) { selection ->
            val capped = minOf(selection.amount, maxCraftable(recipe))
            if (capped > 0) {
                clearWeakQueue(FletchingParams.BOLT_TIP_QUEUE)
                playTipAnim(recipe)
                fletchQueue(
                    FletchingParams.BOLT_TIP_QUEUE,
                    FletchingParams.BOLT_TIP_TICKS,
                    FletchingTask(recipe, capped, completed = 0),
                )
            }
        }
    }

    private fun ProtectedAccess.playTipAnim(recipe: FletchingRecipe) {
        recipe.anim?.internalName?.let(::anim)
        soundSynth(FletchingParams.CHISEL_SOUND)
    }

    private suspend fun ProtectedAccess.processTip(task: FletchingTask) {
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
            playTipAnim(recipe)
            fletchQueue(
                FletchingParams.BOLT_TIP_QUEUE,
                FletchingParams.BOLT_TIP_TICKS,
                FletchingTask(recipe, task.amount, completed),
            )
        } else {
            resetAnim()
        }
    }
}
