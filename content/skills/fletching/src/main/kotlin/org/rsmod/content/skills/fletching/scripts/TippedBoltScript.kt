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

class TippedBoltScript : PluginScript() {

    private val recipes = FletchingRecipe.table("fletching_tipped_bolts")

    override fun ScriptContext.startup() {
        for (recipe in recipes) {
            onOpHeldU(recipe.inputs[0].internalName, recipe.inputs[1].internalName) {
                promptSet(recipe)
            }
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.TIPPED_BOLT_QUEUE) {
            processSet(it.args)
        }
    }

    private suspend fun ProtectedAccess.promptSet(recipe: FletchingRecipe) {
        if (!meetsFletchingLevel(recipe)) {
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                actionType = SkillingActionType.MAKE_SETS,
                entries =
                    listOf(recipe.toSkillMultiEntry(perSetCount = FletchingParams.DART_BOLT_SET_SIZE)),
                maxCountProvider = { inventory, _ ->
                    maxSets(inventory, recipe, FletchingParams.DART_BOLT_SET_SIZE)
                },
            )
        ) { selection ->
            val capped =
                minOf(selection.amount, maxSets(inv, recipe, FletchingParams.DART_BOLT_SET_SIZE))
            if (capped > 0) {
                clearWeakQueue(FletchingParams.TIPPED_BOLT_QUEUE)
                playTipAnim(recipe)
                fletchQueue(
                    FletchingParams.TIPPED_BOLT_QUEUE,
                    FletchingParams.DART_BOLT_SET_TICKS,
                    FletchingTask(recipe, capped, completed = 0),
                )
            }
        }
    }

    private fun ProtectedAccess.playTipAnim(recipe: FletchingRecipe) {
        recipe.anim?.internalName?.let(::anim)
        soundSynth(FletchingParams.FLETCH_SOUND)
    }

    private suspend fun ProtectedAccess.processSet(task: FletchingTask) {
        val recipe = task.recipe
        if (!meetsFletchingLevel(recipe)) {
            resetAnim()
            return
        }
        val count = minOf(FletchingParams.DART_BOLT_SET_SIZE, maxCraftable(recipe))
        if (count <= 0) {
            resetAnim()
            return
        }

        if (!tryCraft(recipe, count = count)) {
            resetAnim()
            return
        }
        statAdvance(FLETCHING_STAT, recipe.xpTenths * count / 10.0)

        val completed = task.completed + 1
        if (completed < task.amount && hasMaterials(recipe)) {
            playTipAnim(recipe)
            fletchQueue(
                FletchingParams.TIPPED_BOLT_QUEUE,
                FletchingParams.DART_BOLT_SET_TICKS,
                FletchingTask(recipe, task.amount, completed),
            )
        } else {
            resetAnim()
        }
    }
}
