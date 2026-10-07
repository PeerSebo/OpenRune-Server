package org.rsmod.content.skills.fletching.scripts

import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpContentHeldU
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.fletching.configs.FletchingParams
import org.rsmod.content.skills.fletching.table.FletchingRecipe
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.slayer.core.SlayerTaskManager
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BroadAmmoScript : PluginScript() {

    private val recipes = FletchingRecipe.table("fletching_broad")

    override fun ScriptContext.startup() {
        for (recipe in recipes) {
            val featherIndex =
                recipe.inputs.indexOfFirst { it.internalName == FletchingParams.FEATHER }
            if (featherIndex == -1) {
                onOpHeldU(recipe.inputs[0].internalName, recipe.inputs[1].internalName) {
                    promptSet(recipe, usedFeather = null)
                }
            } else {
                val base = recipe.inputs[1 - featherIndex]
                onOpContentHeldU(FletchingParams.FEATHER_CONTENT, base) { event ->
                    promptSet(recipe, usedFeather = event.first)
                }
            }
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.BROAD_AMMO_QUEUE) {
            processSet(it.args)
        }
    }

    private suspend fun ProtectedAccess.promptSet(recipe: FletchingRecipe, usedFeather: ItemServerType?) {
        if (!SlayerTaskManager.hasUnlockedReward(this, FletchingParams.BROADER_FLETCHING_BIT)) {
            mesbox(FletchingParams.BROADER_FLETCHING_LOCKED_MESSAGE)
            return
        }
        if (!meetsFletchingLevel(recipe)) {
            return
        }
        val setSize = recipe.broadSetSize
        val materials = recipe.effectiveMaterials(usedFeather)
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                actionType = SkillingActionType.MAKE_SETS,
                entries = listOf(recipe.toSkillMultiEntry(perSetCount = setSize)),
                maxCountProvider = { inventory, _ -> maxSets(inventory, materials, setSize) },
            )
        ) { selection ->
            val capped = minOf(selection.amount, maxSets(inv, materials, setSize))
            if (capped > 0) {
                clearWeakQueue(FletchingParams.BROAD_AMMO_QUEUE)
                playAssemblyAnim(recipe)
                fletchQueue(
                    FletchingParams.BROAD_AMMO_QUEUE,
                    FletchingParams.DART_BOLT_SET_TICKS,
                    FletchingTask(recipe, capped, completed = 0, usedFeather),
                )
            }
        }
    }

    private fun ProtectedAccess.playAssemblyAnim(recipe: FletchingRecipe) {
        recipe.anim?.internalName?.let(::anim)
        soundSynth(FletchingParams.FLETCH_SOUND)
    }

    private suspend fun ProtectedAccess.processSet(task: FletchingTask) {
        val recipe = task.recipe
        if (
            !SlayerTaskManager.hasUnlockedReward(this, FletchingParams.BROADER_FLETCHING_BIT) ||
                !meetsFletchingLevel(recipe)
        ) {
            resetAnim()
            return
        }
        val materials = recipe.effectiveMaterials(task.usedFeather)
        val count = minOf(recipe.broadSetSize, maxCraftable(materials))
        if (count <= 0) {
            resetAnim()
            return
        }

        if (!tryCraft(recipe, materials, count)) {
            resetAnim()
            return
        }
        statAdvance(FLETCHING_STAT, recipe.xpTenths * count / 10.0)

        val completed = task.completed + 1
        if (completed < task.amount && hasMaterials(materials)) {
            playAssemblyAnim(recipe)
            fletchQueue(
                FletchingParams.BROAD_AMMO_QUEUE,
                FletchingParams.DART_BOLT_SET_TICKS,
                FletchingTask(recipe, task.amount, completed, task.usedFeather),
            )
        } else {
            resetAnim()
        }
    }

    private val FletchingRecipe.broadSetSize: Int
        get() = checkNotNull(setSize) { "Missing `set_size` column for broad-ammo row `$rowId`." }
}
