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
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AmmoAssemblyScript : PluginScript() {

    private val arrowRecipes = FletchingRecipe.table("fletching_arrows")
    private val batchRecipes = FletchingRecipe.table("fletching_special_assembly")
    private val javelinRecipes = FletchingRecipe.table("fletching_javelins")
    private val dartBoltRecipes =
        FletchingRecipe.table("fletching_darts") + FletchingRecipe.table("fletching_bolts")

    override fun ScriptContext.startup() {
        for (recipe in arrowRecipes) {
            registerRecipe(
                recipe,
                FletchingParams.ARROW_SET_SIZE,
                FletchingParams.AMMO_QUEUE,
                FletchingParams.AMMO_SET_TICKS,
            )
        }
        for (recipe in batchRecipes) {
            registerRecipe(recipe, 1, FletchingParams.AMMO_QUEUE, checkNotNull(recipe.ticks))
        }
        for (recipe in javelinRecipes) {
            registerRecipe(
                recipe,
                FletchingParams.ARROW_SET_SIZE,
                FletchingParams.JAVELIN_QUEUE,
                FletchingParams.JAVELIN_SET_TICKS,
            )
        }
        for (recipe in dartBoltRecipes) {
            registerRecipe(
                recipe,
                FletchingParams.DART_BOLT_SET_SIZE,
                FletchingParams.DART_BOLT_QUEUE,
                FletchingParams.DART_BOLT_SET_TICKS,
            )
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.AMMO_QUEUE) {
            processSet(
                it.args,
                FletchingParams.ARROW_SET_SIZE,
                FletchingParams.AMMO_QUEUE,
                FletchingParams.AMMO_SET_TICKS,
            )
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.JAVELIN_QUEUE) {
            processSet(
                it.args,
                FletchingParams.ARROW_SET_SIZE,
                FletchingParams.JAVELIN_QUEUE,
                FletchingParams.JAVELIN_SET_TICKS,
            )
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.DART_BOLT_QUEUE) {
            processSet(
                it.args,
                FletchingParams.DART_BOLT_SET_SIZE,
                FletchingParams.DART_BOLT_QUEUE,
                FletchingParams.DART_BOLT_SET_TICKS,
            )
        }
    }

    private fun ScriptContext.registerRecipe(
        recipe: FletchingRecipe,
        setSize: Int,
        queue: String,
        ticks: Int,
    ) {
        val featherIndex = recipe.inputs.indexOfFirst { it.internalName == FletchingParams.FEATHER }
        if (featherIndex == -1) {
            onOpHeldU(recipe.inputs[0].internalName, recipe.inputs[1].internalName) {
                promptSets(recipe, usedFeather = null, setSize, queue, ticks)
            }
        } else {
            val base = recipe.inputs[1 - featherIndex]
            onOpContentHeldU(FletchingParams.FEATHER_CONTENT, base) { event ->
                promptSets(recipe, usedFeather = event.first, setSize, queue, ticks)
            }
        }
    }

    private suspend fun ProtectedAccess.promptSets(
        recipe: FletchingRecipe,
        usedFeather: ItemServerType?,
        setSize: Int,
        queue: String,
        ticks: Int,
    ) {
        if (!meetsFletchingLevel(recipe)) {
            return
        }
        val materials = recipe.effectiveMaterials(usedFeather)
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                actionType = SkillingActionType.MAKE_SETS,
                entries = listOf(recipe.toSkillMultiEntry(
                    perSetCount = if (recipe.isFixedBatch) recipe.outputAmount else setSize)),
                // Prompt quantities count sets, not individual items.
                maxCountProvider = { inventory, _ -> maxSets(inventory, materials, setSize) },
            )
        ) { selection ->
            val capped = minOf(selection.amount, maxSets(inv, materials, setSize))
            if (capped > 0) {
                clearWeakQueue(queue)
                playAssemblyAnim(recipe)
                fletchQueue(queue, ticks, FletchingTask(recipe, capped, completed = 0, usedFeather))
            }
        }
    }

    private fun ProtectedAccess.playAssemblyAnim(recipe: FletchingRecipe) {
        // Assembly sequences loop beyond the production cadence; reset when the batch ends.
        recipe.anim?.internalName?.let(::anim)
        soundSynth(FletchingParams.FLETCH_SOUND)
    }

    private suspend fun ProtectedAccess.processSet(
        task: FletchingTask,
        setSize: Int,
        queue: String,
        ticks: Int,
    ) {
        val recipe = task.recipe
        if (!meetsFletchingLevel(recipe)) {
            resetAnim()
            return
        }
        val materials = recipe.effectiveMaterials(task.usedFeather)
        val count = minOf(if (recipe.isFixedBatch) 1 else setSize, maxCraftable(materials))
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
            fletchQueue(queue, if (recipe.isFixedBatch) checkNotNull(recipe.ticks) else ticks,
                FletchingTask(recipe, task.amount, completed, task.usedFeather))
        } else {
            resetAnim()
        }
    }

    private val FletchingRecipe.isFixedBatch: Boolean
        get() = category == "Batch"
}
